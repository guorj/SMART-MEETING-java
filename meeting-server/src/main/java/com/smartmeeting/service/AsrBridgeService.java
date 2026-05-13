package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.AsrResult;
import com.smartmeeting.asr.XfyunRealtimeClient;
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
 * ASR 桥接服务 - 浏览器音频 → 后端 → 讯飞实时ASR
 *
 * <p>音频流路径:
 * 浏览器 → WebSocket → AudioWebSocketHandler → AsrBridgeService → XfyunRealtimeClient
 *
 * <p>上行节奏与 Python 版 {@code main.py} consumer 对齐：前端每来一包 PCM 即原样转发至讯飞 WebSocket，
 * 不在后端做 40ms 定时凑 1280 字节（前端仍应按 16k/mono/s16le、每帧 1280 字节/40ms 发送，与讯飞文档一致）。
 */
@Slf4j
@Service
public class AsrBridgeService {

    private final XfyunRealtimeClient xfyunClient;
    private final TranscriptMapper transcriptMapper;

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final AudioCacheService audioCacheService;
    private final MeetingHostSessionService meetingHostSessionService;

    private final Map<String, Integer> speakerCounters = new ConcurrentHashMap<>();

    @Value("${meeting.asr.primary:xfyun}")
    private String primaryAsr;

    public AsrBridgeService(XfyunRealtimeClient xfyunClient,
                            @Lazy AudioWebSocketHandler audioWebSocketHandler,
                            AudioCacheService audioCacheService,
                            TranscriptMapper transcriptMapper,
                            @Lazy MeetingHostSessionService meetingHostSessionService) {
        this.xfyunClient = xfyunClient;
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.audioCacheService = audioCacheService;
        this.transcriptMapper = transcriptMapper;
        this.meetingHostSessionService = meetingHostSessionService;

        xfyunClient.setTranscriptCallback(this::onAsrResult);
    }

    /**
     * 开始实时 ASR（为指定会议建立讯飞连接）
     */
    public boolean startRealtimeAsr(String meetingId) {
        if (!"xfyun".equals(primaryAsr)) {
            log.warn("Primary ASR is not xfyun: {}", primaryAsr);
            return false;
        }

        speakerCounters.put(meetingId, 0);

        log.info("【ASR启动】meetingId={}", meetingId);

        boolean connected = xfyunClient.connect(meetingId);
        if (!connected) {
            log.error("Failed to connect to Xfyun ASR for meeting: {}", meetingId);
            return false;
        }

        return true;
    }

    /**
     * 将音频帧转发给 ASR 引擎（与 Python 版一致：收到即发送，不经后端定时缓冲）
     */
    public void sendAudioFrame(String meetingId, byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return;
        }
        audioCacheService.writeAudioChunk(meetingId, pcmData);

        if (!xfyunClient.isConnected() || !meetingId.equals(xfyunClient.getCurrentMeetingId())) {
            return;
        }
        xfyunClient.sendAudio(pcmData);
        if (pcmData.length != 1280) {
            log.debug("【ASR上行】meetingId={}, pcmBytes={} (非1280，依赖前端或网络分包)", meetingId, pcmData.length);
        }
    }

    /**
     * 结束实时 ASR
     */
    public void endRealtimeAsr(String meetingId) {
        log.info("【ASR结束】meetingId={}", meetingId);

        xfyunClient.end();
        speakerCounters.remove(meetingId);
    }

    /**
     * 断开 ASR 连接
     */
    public void disconnect() {
        xfyunClient.disconnect();
    }

    private void onAsrResult(AsrResult result) {
        String meetingId = result.getMeetingId();
        String speaker = "Speaker " + speakerCounters.getOrDefault(meetingId, 0);

        log.info("【ASR转写】meetingId={}, text={}, final={}, last={}",
                meetingId, result.getText(), result.getFinalResult(), result.getIsLast());

        try {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setId(UUID.randomUUID().toString());
            segment.setMeetingId(meetingId);
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
            log.info("【ASR完成】收到最后一条结果，关闭WebSocket: meetingId={}", meetingId);
            audioWebSocketHandler.closeSessionGracefully(meetingId);
        }
    }

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

    public String getAsrProvider() {
        return primaryAsr;
    }

    public boolean isAsrActive() {
        return xfyunClient.isConnected();
    }

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
