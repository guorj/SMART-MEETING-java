package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.AsrResult;
import com.smartmeeting.asr.XfyunRealtimeClient;
import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.service.host.MeetingHostSessionService;
import com.smartmeeting.service.host.RollCallAffirmationMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.entity.TranscriptSegment;

/**
 * ASR 桥接服务：浏览器音频经 WebSocket 转发至讯飞实时识别，并回写转写与点名钩子。
 *
 * <p>音频流路径：
 * 浏览器 → WebSocket → {@link AudioWebSocketHandler} → 本服务 → {@link XfyunRealtimeClient}
 *
 * <p>上行节奏与 Python 版 {@code main.py} consumer 对齐：前端每来一包 PCM 即原样转发至讯飞 WebSocket，
 * 不在后端做 40ms 定时凑 1280 字节（前端仍应按 16k/mono/s16le、每帧 1280 字节/40ms 发送，与讯飞文档一致）。
 *
 * <p>主要协作组件：
 * <ul>
 *   <li>{@link XfyunRealtimeClient} — 讯飞实时 ASR 连接与回调</li>
 *   <li>{@link AudioWebSocketHandler} — 向前端推送转写、优雅关闭会话</li>
 *   <li>{@link AudioCacheService} — 会议音频块缓存</li>
 *   <li>{@link TranscriptMapper} — 转写片段持久化</li>
 *   <li>{@link MeetingHostSessionService} — 答到窗口内定稿文本钩子</li>
 * </ul>
 */
@Slf4j
@Service
public class AsrBridgeService {

    private final XfyunRealtimeClient xfyunClient;
    private final TranscriptMapper transcriptMapper;

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final AudioCacheService audioCacheService;
    private final MeetingHostSessionService meetingHostSessionService;
    private final MeetingAsrProperties asrProperties;
    private final MeetingPresetTypeResolver presetTypeResolver;

    private final Map<String, Integer> speakerCounters = new ConcurrentHashMap<>();

    @Value("${meeting.asr.primary:xfyun}")
    private String primaryAsr;

    /**
     * 构造桥接服务并注册讯飞转写回调。
     *
     * @param xfyunClient                 讯飞实时客户端
     * @param audioWebSocketHandler       音频 WebSocket 处理器（延迟注入避免循环依赖）
     * @param audioCacheService           音频缓存
     * @param transcriptMapper            转写持久化
     * @param meetingHostSessionService   主持会话（延迟注入）
     */
    public AsrBridgeService(XfyunRealtimeClient xfyunClient,
                            @Lazy AudioWebSocketHandler audioWebSocketHandler,
                            AudioCacheService audioCacheService,
                            TranscriptMapper transcriptMapper,
                            @Lazy MeetingHostSessionService meetingHostSessionService,
                            MeetingAsrProperties asrProperties,
                            MeetingPresetTypeResolver presetTypeResolver) {
        this.xfyunClient = xfyunClient;
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.audioCacheService = audioCacheService;
        this.transcriptMapper = transcriptMapper;
        this.meetingHostSessionService = meetingHostSessionService;
        this.asrProperties = asrProperties;
        this.presetTypeResolver = presetTypeResolver;

        xfyunClient.setTranscriptCallback(this::onAsrResult);
    }

    /** 是否启用会中实时转写（由 {@code meeting.asr.realtime-enabled} 控制）。 */
    public boolean isRealtimeEnabled() {
        return asrProperties.isRealtimeEnabled();
    }

    /**
     * 为指定会议启动实时 ASR（建立讯飞 WebSocket 连接）。
     *
     * @param meetingId 会议 ID
     * @return 连接成功为 true；主 ASR 非 xfyun 或连接失败为 false
     */
    public boolean startRealtimeAsr(String meetingId) {
        if (!asrProperties.isRealtimeEnabled()) {
            log.info("【ASR跳过】实时转写已关闭 meetingId={}", meetingId);
            return false;
        }
        if (!"xfyun".equals(primaryAsr)) {
            log.warn("Primary ASR is not xfyun: {}", primaryAsr);
            return false;
        }

        speakerCounters.put(meetingId, 0);
        presetTypeResolver.resolve(meetingId);

        log.info("【ASR启动】meetingId={}", meetingId);

        boolean connected = xfyunClient.connect(meetingId);
        if (!connected) {
            log.error("Failed to connect to Xfyun ASR for meeting: {}", meetingId);
            return false;
        }

        return true;
    }

    /**
     * 将音频帧写入缓存并转发给 ASR 引擎（收到即发送，不经后端定时缓冲）。
     *
     * @param meetingId 会议 ID
     * @param pcmData   PCM 数据（16k/mono/s16le），null 或空则忽略
     */
    public void sendAudioFrame(String meetingId, byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return;
        }
        audioCacheService.writeAudioChunk(meetingId, pcmData);

        if (!asrProperties.isRealtimeEnabled()) {
            return;
        }
        if (!xfyunClient.isConnected() || !meetingId.equals(xfyunClient.getCurrentMeetingId())) {
            return;
        }
        xfyunClient.sendAudio(pcmData);
        if (pcmData.length != 1280) {
            log.debug("【ASR上行】meetingId={}, pcmBytes={} (非1280，依赖前端或网络分包)", meetingId, pcmData.length);
        }
    }

    /**
     * 结束指定会议的实时 ASR 会话（发送结束帧并清理说话人计数）。
     *
     * @param meetingId 会议 ID
     */
    public void endRealtimeAsr(String meetingId) {
        log.info("【ASR结束】meetingId={}", meetingId);

        xfyunClient.end();
        speakerCounters.remove(meetingId);
        presetTypeResolver.evict(meetingId);
    }

    /**
     * 暂停会议时挂起实时 ASR：发送 end 并断开讯飞连接以释放配额，不关浏览器音频 WebSocket。
     *
     * @param meetingId 会议 ID
     */
    public void suspendRealtimeAsr(String meetingId) {
        if (!asrProperties.isRealtimeEnabled()) {
            return;
        }
        String current = xfyunClient.getCurrentMeetingId();
        if (!xfyunClient.isConnected() && (current == null || !meetingId.equals(current))) {
            speakerCounters.remove(meetingId);
            return;
        }
        if (current != null && !meetingId.equals(current)) {
            log.warn("【ASR挂起跳过】meetingId={} 与当前连接 {} 不一致", meetingId, current);
            return;
        }
        log.info("【ASR挂起】meetingId={}，断开讯飞以省配额", meetingId);
        endRealtimeAsr(meetingId);
        try {
            xfyunClient.disconnect();
        } catch (Exception e) {
            log.warn("【ASR挂起】disconnect: {}", e.getMessage());
        }
    }

    /**
     * 恢复会议时按需重建实时 ASR 连接。
     *
     * @param meetingId 会议 ID
     * @return 连接成功为 true；实时转写关闭或连接失败为 false
     */
    public boolean resumeRealtimeAsr(String meetingId) {
        if (isAsrActiveForMeeting(meetingId)) {
            return true;
        }
        return startRealtimeAsr(meetingId);
    }

    /**
     * 指定会议是否已建立且占用的讯飞实时 ASR 连接。
     */
    public boolean isAsrActiveForMeeting(String meetingId) {
        return asrProperties.isRealtimeEnabled()
                && xfyunClient.isConnected()
                && meetingId != null
                && meetingId.equals(xfyunClient.getCurrentMeetingId());
    }

    /**
     * 断开当前讯飞 ASR WebSocket 连接（不区分会议）。
     */
    public void disconnect() {
        xfyunClient.disconnect();
    }

    /**
     * 讯飞转写结果回调：入库、推送前端、点名答到钩子（ls=true 仅表示本段结束，不关浏览器 WS）。
     */
    private void onAsrResult(AsrResult result) {
        String meetingId = result.getMeetingId();
        String speaker = "Speaker " + speakerCounters.getOrDefault(meetingId, 0);

        log.info("【ASR转写】meetingId={}, text={}, final={}, last={}",
                meetingId, result.getText(), result.getFinalResult(), result.getIsLast());

        try {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setId(UUID.randomUUID().toString());
            segment.setMeetingId(meetingId);
            segment.setPresetTypeCode(presetTypeResolver.resolve(meetingId));
            segment.setSpeakerId(speaker);
            segment.setText(result.getText());
            segment.setStartTimeMs(result.getStartTimeMs() != null ? result.getStartTimeMs() : 0);
            segment.setEndTimeMs(result.getEndTimeMs() != null ? result.getEndTimeMs() : 0);
            segment.setIsFinal(result.getFinalResult());
            segment.setConfidence(result.getConfidence() != null ? result.getConfidence() : 1.0);
            segment.setCorrected(false);

            transcriptMapper.insert(segment);
            log.info("【转写入库】segment saved: meetingId={}, text={}", meetingId, result.getText());
        } catch (Exception e) {
            log.warn("Failed to save transcript segment: {}", e.getMessage());
        }

        audioWebSocketHandler.sendTranscript(
                meetingId,
                speaker,
                result.getText(),
                result.getFinalResult()
        );

        String text = result.getText();
        boolean canHookRollCall = meetingId != null && text != null && !text.isBlank();
        boolean rollCallWindow = canHookRollCall && meetingHostSessionService.isRollCallAnswerWindowArmed(meetingId);
        if (rollCallWindow) {
            String preview = text.length() > 80 ? text.substring(0, 80) + "...(truncated)" : text;
            log.debug("roll-call hook: meetingId={}, text='{}'", meetingId, preview);
            try {
                meetingHostSessionService.onRollCallFinalTranscript(meetingId, text);
            } catch (Exception e) {
                log.debug("roll-call transcript hook: {}", e.getMessage());
            }
        } else if (canHookRollCall && looksLikeRollCallReply(text)) {
            String preview = text.length() > 80 ? text.substring(0, 80) + "...(truncated)" : text;
            log.debug("roll-call skipped: answer-like text but window not armed, meetingId={}, text='{}'",
                    meetingId, preview);
        }

        if (Boolean.TRUE.equals(result.getIsLast())) {
            log.debug("【ASR分段结束】ls=true meetingId={}（会中不关闭浏览器音频 WS）", meetingId);
        }
    }

    /**
     * 强制断开指定会议的 ASR（结束会话并断开讯飞连接），用于会议结束等场景。
     *
     * @param meetingId 会议 ID
     */
    public void forceDisconnectAsr(String meetingId) {
        try {
            endRealtimeAsr(meetingId);
        } catch (Exception e) {
            log.warn("endRealtimeAsr: {}", e.getMessage());
        }
        try {
            xfyunClient.disconnect();
        } catch (Exception e) {
            log.warn("xfyun disconnect: {}", e.getMessage());
        }
    }

    /**
     * 返回当前配置的主 ASR 提供商标识。
     *
     * @return 如 {@code xfyun}
     */
    public String getAsrProvider() {
        return primaryAsr;
    }

    /**
     * 判断讯飞 ASR WebSocket 是否已连接。
     *
     * @return 已连接为 true
     */
    public boolean isAsrActive() {
        return xfyunClient.isConnected();
    }

    /** 粗判文本是否像答到回复（用于未开窗时的 debug 日志）。 */
    private static boolean looksLikeRollCallReply(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (RollCallAffirmationMatcher.matches(t)) {
            return true;
        }
        return t.length() <= 8 && (t.contains("到") || t.contains("在") || t.contains("收到") || t.contains("嗯"));
    }
}
