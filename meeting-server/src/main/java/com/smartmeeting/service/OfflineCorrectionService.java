package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.model.OfflineTranscribeRequest;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 会议录音离线 ASR 校正服务（委托 {@link OfflineTranscriptVoiceprintService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineCorrectionService {

    private final OfflineTranscriptVoiceprintService offlineTranscriptVoiceprintService;
    private final TranscriptSegmentHelper transcriptSegmentHelper;
    private final MeetingAsrProperties asrProperties;
    private final ParticipantMapper participantMapper;
    private final VoiceprintMapper voiceprintMapper;

    @Transactional
    public String correct(String meetingId, String audioPath) {
        return correct(OfflineTranscribeRequest.of(meetingId, audioPath, List.of(), 0));
    }

    @Transactional
    public String correct(OfflineTranscribeRequest request) {
        String meetingId = request.getMeetingId();
        String audioPath = request.getAudioPath();
        log.info("Starting offline correction for meeting: {}, audioPath={}", meetingId, audioPath);
        if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
            log.info("Offline correction skipped (realtime segments exist): meetingId={}", meetingId);
            return transcriptSegmentHelper.buildLabeledTranscriptText(
                    transcriptSegmentHelper.listFinalSegments(meetingId));
        }
        if (!asrProperties.isOfflineEnabled()) {
            log.info("Offline correction skipped (meeting.asr.offline-enabled=false): meetingId={}", meetingId);
            return "";
        }

        OfflineTranscribeRequest enriched = enrichFromParticipants(request);
        return offlineTranscriptVoiceprintService.run(enriched);
    }

    private OfflineTranscribeRequest enrichFromParticipants(OfflineTranscribeRequest request) {
        if (request.getFeatureIds() != null && !request.getFeatureIds().isEmpty()
                && request.getParticipantCount() > 0) {
            return request;
        }
        List<Participant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<Participant>().eq(Participant::getMeetingId, request.getMeetingId()));
        List<String> featureIds = resolveIstFeatureIds(participants);
        int count = request.getParticipantCount() > 0 ? request.getParticipantCount() : participants.size();
        if (request.getFeatureIds() != null && !request.getFeatureIds().isEmpty()) {
            return OfflineTranscribeRequest.of(
                    request.getMeetingId(), request.getAudioPath(), request.getFeatureIds(), count);
        }
        log.debug("Offline IST featureIds for meeting {}: count={}, istFeatures={}",
                request.getMeetingId(), count, featureIds.size());
        return OfflineTranscribeRequest.of(request.getMeetingId(), request.getAudioPath(), featureIds, count);
    }

    /**
     * IST upload 用 featureIds：participant 行内 feature_id 优先，否则按 user_id 联查组织声纹库。
     */
    private List<String> resolveIstFeatureIds(List<Participant> participants) {
        int max = Math.max(1, asrProperties.getOfflineIstMaxFeatureIds());
        Set<String> ids = new LinkedHashSet<>();
        for (Participant participant : participants) {
            String fromRow = participant.getFeatureId();
            if (fromRow != null && !fromRow.isBlank()) {
                ids.add(fromRow.trim());
                continue;
            }
            String userId = participant.getUserId();
            if (userId == null || userId.isBlank()) {
                continue;
            }
            Voiceprint voiceprint = voiceprintMapper.selectOne(
                    new LambdaQueryWrapper<Voiceprint>()
                            .and(w -> w.eq(Voiceprint::getFeishuUserId, userId)
                                    .or()
                                    .eq(Voiceprint::getUserId, userId))
                            .last("LIMIT 1"));
            if (voiceprint != null && voiceprint.getFeatureId() != null && !voiceprint.getFeatureId().isBlank()) {
                ids.add(voiceprint.getFeatureId().trim());
            }
        }
        return ids.stream().limit(max).toList();
    }
}
