package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.model.OfflineAsrMessage;
import com.smartmeeting.model.OfflineTranscribeRequest;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会后离线 ASR 执行与完成后编排（触发纪要或收敛 COMPLETED）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineAsrService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingAudioMaterializerService meetingAudioMaterializerService;
    private final AudioNormalizeService audioNormalizeService;
    private final OfflineCorrectionService correctionService;
    private final MeetingMinuteProperties minuteProperties;
    private final DomainEventPublisher domainEventPublisher;
    private final MeetingStateMachineService meetingStateMachineService;
    private final TranscriptSegmentHelper transcriptSegmentHelper;

    public void process(OfflineAsrMessage message) {
        String meetingId = message.getMeetingId();
        try {
            runOfflineAsrSync(meetingId, message.getAudioPath(), message.getFeatureIds());
        } catch (Exception e) {
            log.error("Offline ASR failed for meeting {}", meetingId, e);
        } finally {
            completeAfterOffline(message);
        }
    }

    public void runOfflineAsrSync(String meetingId, String audioPath) {
        runOfflineAsrSync(meetingId, audioPath, null);
    }

    public void runOfflineAsrSync(String meetingId, String audioPath, List<String> featureIds) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            log.error("Offline ASR skipped: meeting not found {}", meetingId);
            return;
        }
        String effectivePath = meetingAudioMaterializerService.materialize(
                meetingId, audioPath, meeting.getSourceAudioUrl());
        if (effectivePath == null || effectivePath.isBlank()) {
            log.error("Offline ASR skipped: audio missing for meeting {}", meetingId);
            return;
        }

        var normalized = audioNormalizeService.normalize(meetingId, effectivePath);
        if (!normalized.usableForAsr()) {
            log.warn("Offline ASR skipped due to audio quality: meetingId={}, status={}, message={}",
                    meetingId, normalized.qualityStatus(), normalized.getQuality().getMessage());
            return;
        }

        List<Participant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<Participant>().eq(Participant::getMeetingId, meetingId));
        List<String> resolvedFeatureIds = featureIds != null && !featureIds.isEmpty()
                ? featureIds
                : participants.stream()
                .map(Participant::getFeatureId)
                .filter(fid -> fid != null && !fid.isEmpty())
                .toList();

        OfflineTranscribeRequest request = OfflineTranscribeRequest.of(
                meetingId, normalized.getNormalizedPath(), resolvedFeatureIds, participants.size());
        correctionService.correct(request);
    }

    private void completeAfterOffline(OfflineAsrMessage message) {
        String meetingId = message.getMeetingId();
        if (minuteProperties.isGenerationEnabled()) {
            domainEventPublisher.publish(new MeetingEndedEvent(
                    meetingId,
                    message.getAudioPath(),
                    message.getFeatureIds() != null ? message.getFeatureIds() : List.of(),
                    message.getModelName(),
                    message.getSentAt() != null ? message.getSentAt() : System.currentTimeMillis()));
            log.info("Offline ASR done, minute generation queued: meetingId={}", meetingId);
            return;
        }
        if (transcriptSegmentHelper.hasAnySegments(meetingId)) {
            Meeting meeting = meetingMapper.selectById(meetingId);
            if (meeting == null) {
                return;
            }
            meetingStateMachineService.apply(meetingId, MeetingEvent.MINUTE_READY);
            meeting.setStatus(MeetingStatus.COMPLETED.name());
            meetingMapper.updateById(meeting);
            log.info("Offline ASR done, meeting completed (minute disabled): id={}", meetingId);
            return;
        }
        log.warn("Offline ASR produced no segments, keeping PROCESSING for retry: id={}", meetingId);
    }
}
