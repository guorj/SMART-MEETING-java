package com.smartmeeting.service;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import com.smartmeeting.asr.AsrResult;
import com.smartmeeting.asr.XfyunRealtimeClient;
import com.smartmeeting.asr.XfyunRealtimeSessionPool;
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
 */
@Slf4j
@Service
public class AsrBridgeService {

    private final XfyunRealtimeSessionPool asrSessionPool;
    private final TranscriptMapper transcriptMapper;

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final AudioCacheService audioCacheService;
    private final MeetingHostSessionService meetingHostSessionService;
    private final MeetingAsrProperties asrProperties;
    private final MeetingPresetTypeResolver presetTypeResolver;

    private final Map<String, Integer> speakerCounters = new ConcurrentHashMap<>();

    @Value("${meeting.asr.primary:xfyun}")
    private String primaryAsr;

    public AsrBridgeService(XfyunRealtimeSessionPool asrSessionPool,
                            @Lazy AudioWebSocketHandler audioWebSocketHandler,
                            AudioCacheService audioCacheService,
                            TranscriptMapper transcriptMapper,
                            @Lazy MeetingHostSessionService meetingHostSessionService,
                            MeetingAsrProperties asrProperties,
                            MeetingPresetTypeResolver presetTypeResolver) {
        this.asrSessionPool = asrSessionPool;
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.audioCacheService = audioCacheService;
        this.transcriptMapper = transcriptMapper;
        this.meetingHostSessionService = meetingHostSessionService;
        this.asrProperties = asrProperties;
        this.presetTypeResolver = presetTypeResolver;
    }

    public boolean isRealtimeEnabled() {
        return asrProperties.isRealtimeEnabled();
    }

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

        boolean connected = asrSessionPool.connect(meetingId, this::onAsrResult);
        if (!connected) {
            log.error("Failed to connect to Xfyun ASR for meeting: {}", meetingId);
            return false;
        }

        return true;
    }

    public void sendAudioFrame(String meetingId, byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            return;
        }
        audioCacheService.writeAudioChunk(meetingId, pcmData);

        if (!asrProperties.isRealtimeEnabled()) {
            return;
        }
        XfyunRealtimeClient client = asrSessionPool.get(meetingId);
        if (client == null || !client.isConnected()) {
            return;
        }
        client.sendAudio(pcmData);
        if (pcmData.length != 1280) {
            log.debug("【ASR上行】meetingId={}, pcmBytes={} (非1280，依赖前端或网络分包)", meetingId, pcmData.length);
        }
    }

    public void endRealtimeAsr(String meetingId) {
        log.info("【ASR结束】meetingId={}", meetingId);

        asrSessionPool.end(meetingId);
        speakerCounters.remove(meetingId);
        presetTypeResolver.evict(meetingId);
    }

    public void suspendRealtimeAsr(String meetingId) {
        if (!asrProperties.isRealtimeEnabled()) {
            return;
        }
        if (!asrSessionPool.isActiveForMeeting(meetingId)) {
            speakerCounters.remove(meetingId);
            return;
        }
        log.info("【ASR挂起】meetingId={}，断开讯飞以省配额", meetingId);
        endRealtimeAsr(meetingId);
        asrSessionPool.disconnect(meetingId);
    }

    public boolean resumeRealtimeAsr(String meetingId) {
        if (isAsrActiveForMeeting(meetingId)) {
            return true;
        }
        return startRealtimeAsr(meetingId);
    }

    public boolean isAsrActiveForMeeting(String meetingId) {
        return asrProperties.isRealtimeEnabled() && asrSessionPool.isActiveForMeeting(meetingId);
    }

    public void disconnect() {
        asrSessionPool.disconnectAll();
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

    public void forceDisconnectAsr(String meetingId) {
        try {
            endRealtimeAsr(meetingId);
        } catch (Exception e) {
            log.warn("endRealtimeAsr: {}", e.getMessage());
        }
        try {
            asrSessionPool.disconnect(meetingId);
        } catch (Exception e) {
            log.warn("xfyun disconnect: {}", e.getMessage());
        }
    }

    public String getAsrProvider() {
        return primaryAsr;
    }

    public boolean isAsrActive() {
        return asrSessionPool.hasAnyActiveSession();
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
