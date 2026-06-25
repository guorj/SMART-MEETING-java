package com.smartmeeting.service;

import com.smartmeeting.api.dto.internal.VoiceprintRelabelResult;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对已有离线转写分段仅重跑 ISV 声纹 1:N 标注（保留文本与时间轴）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineVoiceprintRelabelService {

    private final MeetingMapper meetingMapper;
    private final MeetingAudioMaterializerService meetingAudioMaterializerService;
    private final AudioNormalizeService audioNormalizeService;
    private final TranscriptSegmentHelper transcriptSegmentHelper;
    private final MeetingVoiceprintProperties voiceprintProperties;
    private final OfflineSpeakerLabeler offlineSpeakerLabeler;
    private final VoiceprintService voiceprintService;

    @Transactional
    public VoiceprintRelabelResult relabel(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            throw new BusinessException("meetingId 不能为空");
        }
        Meeting meeting = meetingMapper.selectById(meetingId.trim());
        if (meeting == null) {
            throw new BusinessException("会议不存在: " + meetingId);
        }
        if (!voiceprintProperties.isOfflineLabelEnabled()) {
            throw new BusinessException("meeting.voiceprint.offline-label-enabled=false，无法重标注");
        }
        if (transcriptSegmentHelper.hasFinalRealtimeSegments(meetingId)) {
            throw new BusinessException("会议含会中定稿转写，不支持离线 ISV 重标注: " + meetingId);
        }
        if (!transcriptSegmentHelper.hasAnySegments(meetingId)) {
            throw new BusinessException("会议无转写分段: " + meetingId);
        }

        String audioPath = meetingAudioMaterializerService.materialize(
                meetingId, meeting.getAudioPath(), meeting.getSourceAudioUrl());
        if (audioPath == null || audioPath.isBlank()) {
            throw new BusinessException("无法解析会议 PCM（audio_path / source_audio_url）: " + meetingId);
        }
        if (!Files.isRegularFile(Path.of(audioPath))) {
            throw new BusinessException("PCM 文件不存在: " + audioPath);
        }

        var normalized = audioNormalizeService.normalize(meetingId, audioPath);
        if (!normalized.usableForAsr()) {
            throw new BusinessException("音频质量不足，无法重标注: "
                    + normalized.qualityStatus() + " - " + normalized.getQuality().getMessage());
        }
        String normalizedPath = normalized.getNormalizedPath();

        List<TranscriptSegment> segments = transcriptSegmentHelper.listAllSegments(meetingId);
        Map<String, String> before = snapshotSpeakerFields(segments);

        try {
            voiceprintService.refreshVoiceprintCache();
        } catch (Exception e) {
            log.warn("Voiceprint cache refresh skipped before relabel: {}", e.getMessage());
        }

        offlineSpeakerLabeler.label(meetingId, normalizedPath, segments, List.of());

        List<TranscriptSegment> afterSegments = transcriptSegmentHelper.listAllSegments(meetingId);
        int updatedCount = countSpeakerChanges(before, afterSegments);

        log.info("Voiceprint relabel finished: meetingId={}, segments={}, updated={}, audio={}",
                meetingId, afterSegments.size(), updatedCount, normalizedPath);

        return VoiceprintRelabelResult.builder()
                .meetingId(meetingId)
                .segmentCount(afterSegments.size())
                .updatedCount(updatedCount)
                .audioPathUsed(normalizedPath)
                .message("ISV 声纹重标注完成，未重跑 IST")
                .build();
    }

    private static Map<String, String> snapshotSpeakerFields(List<TranscriptSegment> segments) {
        Map<String, String> snap = new HashMap<>();
        for (TranscriptSegment seg : segments) {
            if (seg.getId() == null) {
                continue;
            }
            snap.put(seg.getId(), fieldKey(seg.getSpeakerId(), seg.getSpeakerName()));
        }
        return snap;
    }

    private static int countSpeakerChanges(Map<String, String> before, List<TranscriptSegment> afterSegments) {
        int updated = 0;
        for (TranscriptSegment seg : afterSegments) {
            if (seg.getId() == null) {
                continue;
            }
            String prev = before.get(seg.getId());
            String now = fieldKey(seg.getSpeakerId(), seg.getSpeakerName());
            if (!Objects.equals(prev, now)) {
                updated++;
            }
        }
        return updated;
    }

    private static String fieldKey(String speakerId, String speakerName) {
        return (speakerId != null ? speakerId : "") + "|" + (speakerName != null ? speakerName : "");
    }
}
