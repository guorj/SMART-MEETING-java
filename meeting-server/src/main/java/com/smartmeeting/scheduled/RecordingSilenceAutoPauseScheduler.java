package com.smartmeeting.scheduled;

import com.smartmeeting.api.config.AudioWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 连续静音自动暂停：释放实时 ASR 配额，与手动 pause 共用 {@link AudioWebSocketHandler#applyPause}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingSilenceAutoPauseScheduler {

    private final AudioWebSocketHandler audioWebSocketHandler;

    @Value("${meeting.audio.silence-auto-pause-min:30}")
    private int silenceAutoPauseMin;

    /**
     * 每 60 秒检查活跃音频连接，超过配置分钟数无 PCM 则自动暂停。
     */
    @Scheduled(fixedRate = 60_000)
    public void checkSilenceAutoPause() {
        if (silenceAutoPauseMin <= 0) {
            return;
        }
        long thresholdMs = silenceAutoPauseMin * 60_000L;
        long now = System.currentTimeMillis();
        int paused = 0;

        for (String meetingId : audioWebSocketHandler.getActiveMeetingIds()) {
            if (audioWebSocketHandler.isMeetingPaused(meetingId)) {
                continue;
            }
            Long last = audioWebSocketHandler.getLastAudioFrameAtMs(meetingId);
            if (last == null) {
                continue;
            }
            if (now - last >= thresholdMs) {
                log.warn("Auto-pausing meeting {} due to silence >= {} min", meetingId, silenceAutoPauseMin);
                audioWebSocketHandler.autoPauseForSilence(meetingId);
                paused++;
            }
        }

        if (paused > 0) {
            log.info("Silence auto-pause applied to {} meeting(s)", paused);
        }
    }
}
