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
 * <p>
 * 有飞书 VC（{@code vc_meeting_url}）且云端录制开启时：先等妙记 {@code recording_ready} 落库后再对 File B 离线转写；
 * 超时 {@code meeting.vc.callback-timeout-min} 后回退 File A。无 VC 时直接对浏览器 PCM 转写。
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
    private final VcRecordingPostMeetingPolicy vcRecordingPolicy;

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

        if (vcRecordingPolicy.isAwaitingVcToken(meeting)) {
            log.info("Post-meeting: awaiting VC recording_ready before offline ASR, meetingId={}", meetingId);
            return MeetingStatus.PROCESSING.name();
        }

        String resolvedAudio = resolveAudioWithCacheFallback(meeting, audioPath);
        return queuePostMeetingWork(meetingId, meeting, resolvedAudio, featureIds, modelName);
    }

    /**
     * 飞书 {@code recording_ready_v1} 落库后继续会后链路（File B 离线转写 / 纪要）。
     * 若离线转写已完成（如超时已走 File A），则不自动重跑。
     */
    public void resumePostMeetingAfterVcReady(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return;
        }
        if (!MeetingStatus.PROCESSING.name().equals(meeting.getStatus())) {
            log.debug("VC ready but meeting not PROCESSING, skip resume: meetingId={}", meetingId);
            return;
        }
        if (transcriptSegmentHelper.hasAnySegments(meetingId)) {
            log.info("VC recording_ready late, offline ASR already done, skip auto resume: meetingId={}", meetingId);
            return;
        }
        if (vcRecordingPolicy.isAwaitingVcToken(meeting)) {
            return;
        }
        String resolvedAudio = resolveAudioWithCacheFallback(meeting, null);
        queuePostMeetingWork(meetingId, meeting, resolvedAudio, List.of(), null);
    }

    /**
     * 等待 VC 回调超时后，用 File A（浏览器 PCM）继续会后链路。
     */
    public void resumePostMeetingWithFileAFallback(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            return;
        }
        if (!MeetingStatus.PROCESSING.name().equals(meeting.getStatus())) {
            return;
        }
        if (!vcRecordingPolicy.isAwaitingVcToken(meeting)) {
            return;
        }
        if (!vcRecordingPolicy.isPastCallbackTimeout(meeting)) {
            return;
        }
        if (transcriptSegmentHelper.hasAnySegments(meetingId)) {
            return;
        }
        log.warn("VC recording_ready timeout, fallback File A for offline ASR: meetingId={}", meetingId);
        String fileA = resolveFileAWithCacheFallback(meeting, null);
        queuePostMeetingWork(meetingId, meeting, fileA, List.of(), null);
    }

    private String queuePostMeetingWork(String meetingId, Meeting meeting, String resolvedAudio,
                                        List<String> featureIds, String modelName) {
        boolean needsOffline = needsOfflineAsr(meetingId, resolvedAudio, meeting);
        boolean needsMinute = minuteProperties.isGenerationEnabled();
        long sentAt = System.currentTimeMillis();

        if (needsOffline) {
            domainEventPublisher.publish(new OfflineAsrRequestedEvent(
                    meetingId, resolvedAudio,
                    featureIds != null ? featureIds : List.of(),
                    modelName, sentAt));
            log.info("Post-meeting: offline ASR queued, meetingId={}, minuteAfter={}, audio={}",
                    meetingId, needsMinute, resolvedAudio);
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

    /**
     * 解析会后转写音频：有 VC 时优先 File B；等 token 期间不回落 File A。
     */
    private String resolveAudioWithCacheFallback(Meeting meeting, String audioPath) {
        if (vcRecordingPolicy.expectsVcRecording(meeting)) {
            AudioSourceResolver.AudioSource resolved = audioSourceResolver.resolve(meeting.getId());
            if (resolved != null && resolved.path() != null) {
                String vcPath = resolved.path().toString();
                meeting.setAudioPath(vcPath);
                meetingMapper.updateById(meeting);
                log.info("Post-meeting using File B (vc_recording): meetingId={}, path={}", meeting.getId(), vcPath);
                return vcPath;
            }
            if (vcRecordingPolicy.isAwaitingVcToken(meeting)) {
                log.info("Post-meeting defer File A: awaiting VC recording, meetingId={}", meeting.getId());
                return null;
            }
            log.warn("File B download failed with vc_minute_token present, fallback File A: meetingId={}",
                    meeting.getId());
        }
        return resolveFileAWithCacheFallback(meeting, audioPath);
    }

    /** 浏览器 PCM（File A）及 cache 回填，不尝试妙记。 */
    private String resolveFileAWithCacheFallback(Meeting meeting, String audioPath) {
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
