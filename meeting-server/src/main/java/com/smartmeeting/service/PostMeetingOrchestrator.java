package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.event.OfflineAsrRequestedEvent;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会后链路编排：按开关独立触发离线转写与纪要生成（顺序：先离线、再纪要）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostMeetingOrchestrator {

    private final MeetingMapper meetingMapper;
    private final MeetingAsrProperties asrProperties;
    private final MeetingMinuteProperties minuteProperties;
    private final TranscriptSegmentHelper transcriptSegmentHelper;
    private final DomainEventPublisher domainEventPublisher;
    private final MeetingStateMachineService meetingStateMachineService;
    private final OfflineAsrService offlineAsrService;
    private final MinuteGenerationService minuteGenerationService;
    private final AudioCacheService audioCacheService;
    private final AudioSourceResolver audioSourceResolver;

    /**
     * 会议结束后的异步编排入口。
     *
     * @return 调用方应返回给客户端的会议状态（PROCESSING 或 COMPLETED）
     */
    public String dispatchAfterMeetingEnded(String meetingId, String audioPath,
                                            List<String> featureIds, String modelName) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            log.warn("Post-meeting dispatch skipped: meeting not found {}", meetingId);
            return MeetingStatus.COMPLETED.name();
        }

        String resolvedAudio = resolveAudioWithCacheFallback(meeting, audioPath);
        boolean needsOffline = needsOfflineAsr(meetingId, resolvedAudio, meeting);
        boolean needsMinute = minuteProperties.isGenerationEnabled();
        long sentAt = System.currentTimeMillis();

        if (needsOffline) {
            domainEventPublisher.publish(new OfflineAsrRequestedEvent(
                    meetingId, resolvedAudio,
                    featureIds != null ? featureIds : List.of(),
                    modelName, sentAt));
            log.info("Post-meeting: offline ASR queued, meetingId={}, minuteAfter={}", meetingId, needsMinute);
            return MeetingStatus.PROCESSING.name();
        }
        if (needsMinute) {
            domainEventPublisher.publish(new MeetingEndedEvent(
                    meetingId, resolvedAudio,
                    featureIds != null ? featureIds : List.of(),
                    modelName, sentAt));
            log.info("Post-meeting: minute generation queued, meetingId={}", meetingId);
            return MeetingStatus.PROCESSING.name();
        }

        completeMeetingWithoutPostProcessing(meetingId);
        log.info("Post-meeting: no offline/minute work, meeting completed directly: id={}", meetingId);
        return MeetingStatus.COMPLETED.name();
    }

    /**
     * 飞书手动「重新生成纪要」：同步先离线（若需要）再生成纪要，不依赖 generation-enabled。
     */
    public void triggerRegenerateMinutes(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new IllegalArgumentException("会议不存在: " + meetingId);
        }
        String audioPath = resolveAudioWithCacheFallback(meeting, null);
        if (needsOfflineAsr(meetingId, audioPath, meeting)) {
            offlineAsrService.runOfflineAsrSync(meetingId, audioPath);
        }
        minuteGenerationService.generateMinute(meetingId, audioPath);
    }

    public boolean needsOfflineAsr(String meetingId, String audioPath, Meeting meeting) {
        if (!asrProperties.isOfflineEnabled()) {
            return false;
        }
        if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
            return false;
        }
        return hasResolvableAudio(audioPath, meeting);
    }

    private void completeMeetingWithoutPostProcessing(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return;
        }
        meetingStateMachineService.apply(meetingId, MeetingEvent.MINUTE_READY);
        meeting.setStatus(MeetingStatus.COMPLETED.name());
        meetingMapper.updateById(meeting);
    }

    private String resolveAudioPath(String audioPath, Meeting meeting) {
        if (audioPath != null && !audioPath.isBlank()) {
            return audioPath;
        }
        if (meeting.getAudioPath() != null && !meeting.getAudioPath().isBlank()) {
            return meeting.getAudioPath();
        }
        return meeting.getSourceAudioUrl();
    }

    private boolean hasResolvableAudio(String audioPath, Meeting meeting) {
        if (audioPath != null && !audioPath.isBlank()) {
            return true;
        }
        if (meeting.getAudioPath() != null && !meeting.getAudioPath().isBlank()) {
            return true;
        }
        return meeting.getSourceAudioUrl() != null && !meeting.getSourceAudioUrl().isBlank();
    }

    /** DB/入参无路径时，若 WebSocket cache 已落盘则回填 {@code audio_path}。 */
    private String resolveAudioWithCacheFallback(Meeting meeting, String audioPath) {
        // 优先：AudioSourceResolver 尝试拉妙记音视频（File B），失败回退 File A
        AudioSourceResolver.AudioSource resolved = audioSourceResolver.resolve(meeting.getId());
        if (resolved != null && resolved.path() != null) {
            String vcPath = resolved.path().toString();
            meeting.setAudioPath(vcPath);
            meetingMapper.updateById(meeting);
            log.info("Post-meeting using File B (vc_recording): meetingId={}, path={}", meeting.getId(), vcPath);
            return vcPath;
        }

        // 回退：原 File A 逻辑
        String resolvedAudio = resolveAudioPath(audioPath, meeting);
        if (resolvedAudio != null && !resolvedAudio.isBlank()) {
            return resolvedAudio;
        }
        return audioCacheService.findExistingCachePath(meeting.getId())
                .map(cached -> {
                    meeting.setAudioPath(cached);
                    meetingMapper.updateById(meeting);
                    log.info("Attached cached audio path for meeting: {}, path={}", meeting.getId(), cached);
                    return cached;
                })
                .orElse(null);
    }
}
