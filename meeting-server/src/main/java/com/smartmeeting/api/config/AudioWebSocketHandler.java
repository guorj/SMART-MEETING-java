package com.smartmeeting.api.config;

import com.smartmeeting.service.AsrBridgeService;
import com.smartmeeting.service.RecordingService;
import com.smartmeeting.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 音频处理器 - 接收浏览器 PCM 音频帧
 * 路径: /ws/audio/{meetingId}?token=xxx
 */
@Slf4j
@Component
public class AudioWebSocketHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;
    private final AsrBridgeService asrBridgeService;
    private final RecordingService recordingService;

    // 会议ID → WebSocket Session 映射
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // 会议ID → 音频帧序列号
    private final Map<String, Long> frameCounters = new ConcurrentHashMap<>();

    // 会议ID → 是否暂停
    private final Map<String, Boolean> pausedMeetings = new ConcurrentHashMap<>();

    public AudioWebSocketHandler(JwtUtil jwtUtil, AsrBridgeService asrBridgeService,
                                  RecordingService recordingService) {
        this.jwtUtil = jwtUtil;
        this.asrBridgeService = asrBridgeService;
        this.recordingService = recordingService;
    }

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

        // 验证 token 中的 meetingId
        try {
            Claims claims = jwtUtil.parseToken(token);
            String tokenMeetingId = claims.get("meetingId", String.class);
            if (!meetingId.equals(tokenMeetingId)) {
                log.warn("Token meetingId mismatch: expected={}, got={}", meetingId, tokenMeetingId);
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Token mismatch"));
                return;
            }
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Token parse error"));
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

        log.info("Audio WS connected: meetingId={}, sessionId={}", meetingId, session.getId());

        // 发送 session 信息给客户端
        TextMessage sessionMsg = new TextMessage(
                String.format("{\"type\":\"session\",\"session_token\":\"%s\",\"meetingId\":\"%s\"}",
                        session.getId(), meetingId));
        session.sendMessage(sessionMsg);

        // 启动实时 ASR
        boolean asrStarted = asrBridgeService.startRealtimeAsr(meetingId);
        if (asrStarted) {
            sendText(session, "{\"type\":\"asr_started\",\"provider\":\"xfyun\"}");
        } else {
            sendText(session, "{\"type\":\"asr_warning\",\"message\":\"ASR 连接失败，仅缓存音频\"}");
        }

        // 开始录音
        try {
            String audioPath = recordingService.startRecording(meetingId);
            sendText(session, "{\"type\":\"recording_started\",\"audioPath\":\"" + audioPath + "\"}");
        } catch (Exception e) {
            log.warn("Failed to start recording via service: {}", e.getMessage());
            // 不阻塞，继续运行
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String meetingId = extractMeetingId(session);

        if (message instanceof BinaryMessage) {
            // 收到 PCM 音频帧
            ByteBuffer buffer = ((BinaryMessage) message).getPayload();
            byte[] pcmData = new byte[buffer.remaining()];
            buffer.get(pcmData);

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
     * 处理控制消息 (pause/resume/stop/pong)
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
                recordingService.pauseRecording(meetingId);
                pausedMeetings.put(meetingId, true);
                log.info("Recording paused for meeting: {}", meetingId);
                sendText(session, "{\"type\":\"paused\",\"meetingId\":\"" + meetingId + "\"}");
            } else if ("resume".equals(type)) {
                recordingService.resumeRecording(meetingId);
                pausedMeetings.put(meetingId, false);
                log.info("Recording resumed for meeting: {}", meetingId);
                sendText(session, "{\"type\":\"resumed\",\"meetingId\":\"" + meetingId + "\"}");
            } else if ("stop".equals(type)) {
                log.info("Recording stopped for meeting: {}", meetingId);
                try {
                    Map<String, Object> result = recordingService.stopRecording(meetingId);
                    sendText(session, "{\"type\":\"stopped\",\"meetingId\":\"" + meetingId + 
                            "\",\"duration\":" + result.get("durationSeconds") + "}");
                } catch (Exception e) {
                    log.warn("Error stopping recording: {}", e.getMessage());
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

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error: {}", session.getId(), exception);
    }

        @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String meetingId = extractMeetingId(session);
        activeSessions.remove(meetingId);
        frameCounters.remove(meetingId);
        pausedMeetings.remove(meetingId);

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

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // --- 工具方法 ---

    private String extractMeetingId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

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
     * 发送实时转写结果到指定会议的所有客户端
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
     * 发送心跳
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
     * 获取活跃连接数
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 检查会议是否有活跃连接
     */
    public boolean hasActiveSession(String meetingId) {
        return activeSessions.containsKey(meetingId);
    }

    /**
     * 优雅关闭WebSocket（等待ASR完成后调用）
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
