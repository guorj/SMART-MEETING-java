package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.OfflineIstOptions;
import com.smartmeeting.asr.OfflineIstParamBuilder;
import com.smartmeeting.asr.XfyunOfflineClient;
import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.model.OfflineTranscribeRequest;
import com.smartmeeting.repository.TranscriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * 会后离线转写：说话人分离 + ISV 声纹 1:N + 回写 {@code int_transcript_segment}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineTranscriptVoiceprintService {

    private final XfyunOfflineClient xfyunOfflineClient;
    private final TranscriptMapper transcriptMapper;
    private final TranscriptSegmentHelper transcriptSegmentHelper;
    private final MeetingPresetTypeResolver presetTypeResolver;
    private final MeetingVoiceprintProperties voiceprintProperties;
    private final MeetingAsrProperties asrProperties;
    private final OfflineSpeakerLabeler offlineSpeakerLabeler;

    @Transactional
    public String run(String meetingId, String audioPath) {
        return run(OfflineTranscribeRequest.of(meetingId, audioPath, List.of(), 0));
    }

    @Transactional
    public String run(OfflineTranscribeRequest request) {
        String meetingId = request.getMeetingId();
        String audioPath = request.getAudioPath();

        if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
            log.info("Skip offline transcript: meeting {} already has final realtime segments", meetingId);
            return "";
        }
        if (audioPath == null || audioPath.isBlank() || !Files.exists(Paths.get(audioPath))) {
            log.warn("Offline transcript skipped: audio missing for meeting {}", meetingId);
            return "";
        }
        try {
            long fileSize = Files.size(Paths.get(audioPath));
            if (fileSize == 0) {
                log.warn("Offline transcript skipped: empty audio for meeting {}", meetingId);
                return "";
            }
        } catch (IOException e) {
            log.warn("Offline transcript skipped: cannot read audio {}", audioPath);
            return "";
        }

        OfflineIstOptions istOptions = OfflineIstParamBuilder.resolve(
                asrProperties, request.getFeatureIds(), request.getParticipantCount());
        log.info("Offline IST options for meeting {}: roleType={}, roleNum={}, features={}",
                meetingId, istOptions.roleType(), istOptions.roleNum(), request.getFeatureIds().size());

        List<TranscriptSegment> segments = xfyunOfflineClient.transcribe(audioPath, istOptions);
        if (segments == null || segments.isEmpty()) {
            log.warn("Offline ASR empty for meeting {}", meetingId);
            return "";
        }

        Integer presetCode = presetTypeResolver.resolve(meetingId);
        for (TranscriptSegment seg : segments) {
            seg.setMeetingId(meetingId);
            seg.setPresetTypeCode(presetCode);
        }

        transcriptMapper.delete(new LambdaQueryWrapper<TranscriptSegment>()
                .eq(TranscriptSegment::getMeetingId, meetingId));
        for (TranscriptSegment seg : segments) {
            transcriptMapper.insert(seg);
        }
        log.info("Offline ASR persisted {} segments for meeting {}", segments.size(), meetingId);

        if (voiceprintProperties.isOfflineLabelEnabled()) {
            offlineSpeakerLabeler.label(meetingId, audioPath, segments, request.getFeatureIds());
        }

        List<TranscriptSegment> refreshed = transcriptSegmentHelper.listFinalSegments(meetingId);
        String labeled = transcriptSegmentHelper.buildLabeledTranscriptText(refreshed);
        log.info("Offline labeled transcript ready: meeting={}, length={}", meetingId, labeled.length());
        return labeled;
    }
}
