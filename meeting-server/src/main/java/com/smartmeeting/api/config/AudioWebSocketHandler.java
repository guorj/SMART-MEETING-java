package com.smartmeeting.api.config;

import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.service.AsrBridgeService;
import com.smartmeeting.service.RecordingService;
import com.smartmeeting.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 音频处理器：接收浏览器 PCM 音频帧并桥接 ASR 与本地录音。
 * <p>
 * 连接路径：{@code /ws/audio/{meetingId}?token=xxx}。同一会议仅保留最新连接；
 * 支持文本控制消息 pause/resume/stop/pong。
 *
 * @see AsrBridgeService
 * @see RecordingService
 */
@Slf4j
@Component
public class AudioWebSocketHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;
    private final AsrBridgeService asrBridgeService;
    private final RecordingService recordingService;
    private final MeetingAsrProperties asrProperties;

    /** 会议 ID → 当前活跃 WebSocket Session */
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    /** 会议 ID → 已接收音频帧计数 */
    private final Map<String, Long> frameCounters = new ConcurrentHashMap<>();

    /** 会议 ID → 是否处于暂停推流状态 */
    private final Map<String, Boolean> pausedMeetings = new ConcurrentHashMap<>();

    /** 会议 ID → 最近一次收到 PCM 的时刻（毫秒），用于静音自动暂停 */
    private final Map<String, Long> lastAudioFrameAtMs = new ConcurrentHashMap<>();

    /**
     * @param jwtUtil           JWT 校验与参会 token 解析
     * @param asrBridgeService  实时 ASR 桥接
     * @param recordingService  本地录音服务
     */
    public AudioWebSocketHandler(JwtUtil jwtUtil, AsrBridgeService asrBridgeService,
                                  RecordingService recordingService,
                                  MeetingAsrProperties asrProperties) {
        this.jwtUtil = jwtUtil;
        this.asrBridgeService = asrBridgeService;
        this.recordingService = recordingService;
        this.asrProperties = asrProperties;
    }

    /**
     * 连接建立：校验 token、启动 ASR 与录音，并向客户端下发 session 信息。
     *
     * @param session WebSocket 会话
     * @throws Exception 发送初始消息失败时
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String meetingId = extractMeetingId(session);
        String token = extractToken(session);

        // 验证 JWT token
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("WebSocket auth failed for meeting: {}", meetingId);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }

        JwtUtil.ParticipantMeetingToken participantToken;
        try {
            participantToken = jwtUtil.parseParticipantMeetingToken(token, meetingId);
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Token parse error"));
            return;
        }
        if (!participantToken.canPushAudio()) {
            log.warn("Audio WS rejected: join-only token for meeting {}", meetingId);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Online join link cannot push audio"));
            return;
        }

        // 如果已有活跃连接，关闭旧的
        WebSocketSession oldSession = activeSessions.put(meetingId, session);
        if (oldSession != null && oldSession.isOpen()) {
            log.info("Closing previous WebSocket session for meeting: {}", meetingId);
            oldSession.close(CloseStatus.NORMAL.withReason("Replaced by new connection"));
        }

        frameCounters.put(meetingId, 0L);
        pausedMeetings.put(meetingId, false);
        lastAudioFrameAtMs.put(meetingId, System.currentTimeMillis());

        log.info("Audio WS connected: meetingId={}, sessionId={}", meetingId, session.getId());

        // 发送 session 信息给客户端
        TextMessage sessionMsg = new TextMessage(
                String.format("{\"type\":\"session\",\"session_token\":\"%s\",\"meetingId\":\"%s\"}",
                        session.getId(), meetingId));
        session.sendMessage(sessionMsg);

        // 启动实时 ASR（可由 meeting.asr.realtime-enabled 关闭）
        if (!asrProperties.isRealtimeEnabled()) {
            sendText(session, "{\"type\":\"asr_disabled\",\"message\":\"实时转写已关闭，仅录音\"}");
        } else {
            boolean asrStarted = asrBridgeService.startRealtimeAsr(meetingId);
            if (asrStarted) {
                sendText(session, "{\"type\":\"asr_started\",\"provider\":\"xfyun\"}");
            } else {
                sendText(session, "{\"type\":\"asr_warning\",\"message\":\"ASR 连接失败，仅缓存音频\"}");
            }
        }

        // 开始或重连录音：已在 RECORDING/PAUSED 时不重复 startRecording
        RecordingService.RecordingState existingState = recordingService.getRecordingState(meetingId);
        if (existingState == RecordingService.RecordingState.RECORDING) {
            sendText(session, "{\"type\":\"recording_rejoined\",\"meetingId\":\"" + meetingId
                    + "\",\"state\":\"RECORDING\"}");
        } else if (existingState == RecordingService.RecordingState.PAUSED) {
            pausedMeetings.put(meetingId, true);
            sendText(session, "{\"type\":\"recording_rejoined\",\"meetingId\":\"" + meetingId
                    + "\",\"state\":\"PAUSED\"}");
        } else {
            tryStartRecording(session, meetingId);
        }
    }

    /** 启动或恢复录音；FAST_START 竞态时由首帧 PCM 再次调用。 */
    private void tryStartRecording(WebSocketSession session, String meetingId) {
        if (recordingService.getRecordingState(meetingId) != null) {
            return;
        }
        String audioPath = recordingService.ensureRecordingStarted(meetingId);
        if (audioPath != null && !audioPath.isBlank()) {
            sendText(session, "{\"type\":\"recording_started\",\"audioPath\":\"" + audioPath + "\"}");
        }
    }

    /**
     * 处理入站消息：二进制帧转发 ASR；文本帧作为控制指令。
     *
     * @param session WebSocket 会话
     * @param message 二进制 PCM 或文本控制 JSON
     * @throws Exception 消息处理异常
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String meetingId = extractMeetingId(session);

        if (message instanceof BinaryMessage) {
            // 收到 PCM 音频帧
            ByteBuffer buffer = ((BinaryMessage) message).getPayload();
            byte[] pcmData = new byte[buffer.remaining()];
            buffer.get(pcmData);

            lastAudioFrameAtMs.put(meetingId, System.currentTimeMillis());

            if (recordingService.getRecordingState(meetingId) == null) {
                tryStartRecording(session, meetingId);
            }

            // 如果暂停了，不发送给 ASR
            if (Boolean.TRUE.equals(pausedMeetings.get(meetingId))) {
                log.debug("Dropping audio frame for paused meeting: {}", meetingId);
                return;
            }

            // 转发给 ASR 桥接服务（同时缓存）
            asrBridgeService.sendAudioFrame(meetingId, pcmData);

            // 更新帧计数器
            long frameNum = frameCounters.merge(meetingId, 1L, Long::sum);

            // 每 50 帧记录一次日志
            if (frameNum % 50 == 0) {
                log.debug("Audio frame #{} for meeting: {} ({} bytes)",
                        frameNum, meetingId, pcmData.length);
            }

        } else if (message instanceof TextMessage) {
            // 收到控制消息
            String payload = ((TextMessage) message).getPayload();
            handleControlMessage(session, meetingId, payload);
        }
    }

    /**
     * 处理控制消息（pause / resume / stop / pong）。
     *
     * @param session   WebSocket 会话
     * @param meetingId 会议 ID
     * @param payload   文本 JSON 载荷
     */
    private void handleControlMessage(WebSocketSession session, String meetingId, String payload) {
        try {
            // 解析 type 字段
            String type = null;
            int typeIdx = payload.indexOf("\"type\"");
            if (typeIdx >= 0) {
                int colonIdx = payload.indexOf(':', typeIdx);
                int startIdx = payload.indexOf('"', colonIdx + 1);
                int endIdx = payload.indexOf('"', startIdx + 1);
                if (startIdx > 0 && endIdx > startIdx) {
                    type = payload.substring(startIdx + 1, endIdx);
                }
            }

            if ("pause".equals(type)) {
                applyPause(session, meetingId, false);
            } else if ("resume".equals(type)) {
                applyResume(session, meetingId);
            } else if ("stop".equals(type)) {
                log.info("Recording stopped for meeting: {}", meetingId);
                try {
                    Map<String, Object> result = recordingService.stopRecording(meetingId);
                    sendText(session, "{\"type\":\"stopped\",\"meetingId\":\"" + meetingId + 
                            "\",\"duration\":" + result.get("durationSeconds") + "}");
                } catch (Exception e) {
                    log.warn("Error stopping recording: {}", e.getMessage());
                    recordingService.ensureRecordingStarted(meetingId);
                    try {
                        Map<String, Object> result = recordingService.stopRecording(meetingId);
                        sendText(session, "{\"type\":\"stopped\",\"meetingId\":\"" + meetingId +
                                "\",\"duration\":" + result.get("durationSeconds") + "}");
                    } catch (Exception retryEx) {
                        log.warn("Retry stop recording failed: {}", retryEx.getMessage());
                    }
                }
                asrBridgeService.endRealtimeAsr(meetingId);
                // 不立即关闭，等待ASR返回最后一条结果后再关闭
            } else if ("pong".equals(type)) {
                // 心跳响应，忽略
            } else {
                log.debug("Control message: type={}, payload={}", type, payload);
            }
        } catch (Exception e) {
            log.warn("Failed to handle control message: {}", payload, e);
        }
    }

    /**
     * 暂停推流：更新录音态、挂起讯飞 ASR（释放配额），保持浏览器 WebSocket。
     *
     * @param autoPause true 表示静音超时触发的自动暂停
     */
    void applyPause(WebSocketSession session, String meetingId, boolean autoPause) {
        try {
            recordingService.pauseRecording(meetingId);
        } catch (Exception e) {
            log.warn("pauseRecording skipped for {}: {}", meetingId, e.getMessage());
        }
        pausedMeetings.put(meetingId, true);
        asrBridgeService.suspendRealtimeAsr(meetingId);
        log.info("Recording paused for meeting: {} (auto={})", meetingId, autoPause);
        if (autoPause) {
            sendText(session, "{\"type\":\"auto_paused\",\"meetingId\":\"" + meetingId
                    + "\",\"message\":\"连续静音超时，已自动暂停推流与实时转写\"}");
        } else {
            sendText(session, "{\"type\":\"paused\",\"meetingId\":\"" + meetingId + "\"}");
        }
    }

    /**
     * 恢复推流：重建讯飞 ASR（若需要），并通知客户端。
     */
    void applyResume(WebSocketSession session, String meetingId) {
        try {
            recordingService.resumeRecording(meetingId);
        } catch (Exception e) {
            log.warn("resumeRecording skipped for {}: {}", meetingId, e.getMessage());
        }
        pausedMeetings.put(meetingId, false);
        lastAudioFrameAtMs.put(meetingId, System.currentTimeMillis());
        boolean asrOk = false;
        if (asrProperties.isRealtimeEnabled()) {
            asrOk = asrBridgeService.resumeRealtimeAsr(meetingId);
            if (asrOk) {
                sendText(session, "{\"type\":\"asr_reconnected\",\"meetingId\":\"" + meetingId + "\"}");
            } else {
                sendText(session, "{\"type\":\"asr_warning\",\"meetingId\":\"" + meetingId
                        + "\",\"message\":\"实时转写重连失败，仅继续录音\"}");
            }
        }
        log.info("Recording resumed for meeting: {}, asrOk={}", meetingId, asrOk);
        sendText(session, "{\"type\":\"resumed\",\"meetingId\":\"" + meetingId + "\"}");
    }

    /**
     * 供静音检测任务读取：会议是否处于暂停推流。
     */
    public boolean isMeetingPaused(String meetingId) {
        return Boolean.TRUE.equals(pausedMeetings.get(meetingId));
    }

    /**
     * 供静音检测任务读取：最近一次收到音频帧的时间戳（毫秒）。
     */
    public Long getLastAudioFrameAtMs(String meetingId) {
        return lastAudioFrameAtMs.get(meetingId);
    }

    /**
     * 供静音检测任务：对活跃会话执行自动暂停。
     */
    public void autoPauseForSilence(String meetingId) {
        WebSocketSession session = activeSessions.get(meetingId);
        if (session == null || !session.isOpen()) {
            return;
        }
        if (Boolean.TRUE.equals(pausedMeetings.get(meetingId))) {
            return;
        }
        applyPause(session, meetingId, true);
    }

    /**
     * 传输层错误回调。
     *
     * @param session   WebSocket 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error: {}", session.getId(), exception);
    }

    /**
     * 连接关闭：清理会话映射并结束 ASR（不自动结束会议）。
     *
     * @param session     WebSocket 会话
     * @param closeStatus 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String meetingId = extractMeetingId(session);
        activeSessions.remove(meetingId);
        frameCounters.remove(meetingId);
        pausedMeetings.remove(meetingId);
        lastAudioFrameAtMs.remove(meetingId);

        // 结束 ASR（但不结束会议，等待用户发送飞书指令"结束会议"）
        try {
            asrBridgeService.endRealtimeAsr(meetingId);
        } catch (Exception e) {
            log.warn("Error ending ASR for meeting: {}", meetingId, e);
        }

        // 参考 Python 版：不自动结束会议，只关闭 ASR
        // 会议结束由飞书指令触发："结束会议"
        
        log.info("Audio WS closed: meetingId={}, code={}, reason={}",
                meetingId, closeStatus.getCode(), closeStatus.getReason());
    }

    /**
     * 不支持分片消息。
     *
     * @return 固定为 false
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 从 WebSocket URI 路径解析会议 ID（{@code /ws/audio/{meetingId}}）。
     *
     * @param session WebSocket 会话
     * @return 会议 ID，解析失败时为 null
     */
    private String extractMeetingId(WebSocketSession session) {
        return WebSocketMeetingIdPaths.meetingIdFromPath(
                session.getUri() != null ? session.getUri().getPath() : null);
    }

    /**
     * 从查询参数解析 {@code token=} JWT。
     *
     * @param session WebSocket 会话
     * @return token 字符串，缺失时为 null
     */
    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    /**
     * 向客户端发送文本帧（连接已关闭时静默忽略）。
     *
     * @param session WebSocket 会话
     * @param text    JSON 或纯文本
     */
    private void sendText(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.warn("Failed to send WS message", e);
        }
    }

    /**
     * 向指定会议的活跃客户端推送实时转写结果。
     *
     * @param meetingId 会议 ID
     * @param speaker   说话人标识
     * @param text      转写文本
     * @param isFinal   是否为最终结果句
     */
    public void sendTranscript(String meetingId, String speaker, String text, boolean isFinal) {
        WebSocketSession session = activeSessions.get(meetingId);
        if (session != null && session.isOpen()) {
            try {
                String json = String.format(
                        "{\"type\":\"transcript\",\"speaker\":\"%s\",\"text\":\"%s\",\"isFinal\":%b}",
                        speaker.replace("\"", "\\\""),
                        text.replace("\"", "\\\""),
                        isFinal);
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.warn("Failed to send transcript", e);
            }
        }
        else {
            log.info("【推送失败】meetingId={}未找到session, 当前activeSessions={}", meetingId, activeSessions.keySet());
        }
    }

    /**
     * 向指定会议客户端发送心跳 ping。
     *
     * @param meetingId 会议 ID
     */
    public void sendPing(String meetingId) {
        WebSocketSession session = activeSessions.get(meetingId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"ping\"}"));
            } catch (Exception e) {
                log.warn("Failed to send ping", e);
            }
        }
    }

    /**
     * 获取当前活跃 WebSocket 连接数。
     *
     * @return 活跃 session 数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 当前存在活跃音频 WebSocket 的会议 ID 快照。
     */
    public Set<String> getActiveMeetingIds() {
        return Set.copyOf(activeSessions.keySet());
    }

    /**
     * 检查指定会议是否存在活跃音频连接。
     *
     * @param meetingId 会议 ID
     * @return 有活跃连接时为 true
     */
    public boolean hasActiveSession(String meetingId) {
        return activeSessions.containsKey(meetingId);
    }

    /**
     * 在 ASR 完成后优雅关闭 WebSocket 连接。
     *
     * @param meetingId 会议 ID
     */
    public void closeSessionGracefully(String meetingId) {
        WebSocketSession session = activeSessions.get(meetingId);
        if (session != null && session.isOpen()) {
            try {
                log.info("【WebSocket关闭】meetingId={}, 原因=ASR完成" , meetingId);
                session.close(CloseStatus.NORMAL.withReason("ASR completed")); 
            } catch (Exception e) {
                log.warn("关闭WebSocket失败", e);
            }
        }
    }
}
