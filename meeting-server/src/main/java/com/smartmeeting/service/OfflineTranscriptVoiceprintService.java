package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunOfflineClient;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.util.PcmSliceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会后离线转写：说话人分离 + ISV 声纹 1:N + 回写 {@code int_transcript_segment}。
 * <p>
 * 仅在没有会中实时定稿分段时替换库内转写；实时 ASR 开启且已有分段时由调用方走另一分支。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineTranscriptVoiceprintService {

    private final XfyunOfflineClient xfyunOfflineClient;
    private final TranscriptMapper transcriptMapper;
    private final TranscriptSegmentHelper transcriptSegmentHelper;
    private final VoiceprintService voiceprintService;
    private final MeetingPresetTypeResolver presetTypeResolver;
    private final MeetingVoiceprintProperties voiceprintProperties;

    /**
     * 离线转写并声纹标注；若已有实时定稿分段则跳过并返回空串。
     *
     * @param meetingId 会议 ID
     * @param audioPath 本地 PCM 路径
     * @return 带说话人标签的全文；跳过或失败时返回空串
     */
    @Transactional
    public String run(String meetingId, String audioPath) {
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

        List<TranscriptSegment> segments = xfyunOfflineClient.transcribe(audioPath);
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
            labelSpeakersByVoiceprint(meetingId, audioPath, segments);
        }

        List<TranscriptSegment> refreshed = transcriptSegmentHelper.listFinalSegments(meetingId);
        String labeled = transcriptSegmentHelper.buildLabeledTranscriptText(refreshed);
        log.info("Offline labeled transcript ready: meeting={}, length={}", meetingId, labeled.length());
        return labeled;
    }

    private void labelSpeakersByVoiceprint(String meetingId, String audioPath, List<TranscriptSegment> segments) {
        Map<String, List<TranscriptSegment>> bySpeaker = segments.stream()
                .filter(s -> s.getSpeakerId() != null && !s.getSpeakerId().isBlank())
                .collect(Collectors.groupingBy(TranscriptSegment::getSpeakerId, LinkedHashMap::new, Collectors.toList()));

        int processed = 0;
        for (Map.Entry<String, List<TranscriptSegment>> entry : bySpeaker.entrySet()) {
            if (processed >= voiceprintProperties.getOfflineMaxSpeakers()) {
                log.warn("Offline voiceprint speaker cap reached for meeting {}", meetingId);
                break;
            }
            String clusterId = entry.getKey();
            List<TranscriptSegment> clusterSegs = entry.getValue();
            clusterSegs.sort(Comparator.comparingInt(s ->
                    -(segmentDurationMs(s))));

            SliceWindow window = pickRepresentativeWindow(clusterSegs);
            if (window == null) {
                continue;
            }

            byte[] slice;
            try {
                slice = PcmSliceUtil.slice(audioPath, window.startMs, window.endMs, PcmSliceUtil.DEFAULT_SAMPLE_RATE);
            } catch (IOException e) {
                log.warn("PCM slice failed for {}: {}", meetingId, e.getMessage());
                continue;
            }
            if (slice.length < 1600) {
                log.debug("Slice too short for speaker {} meeting {}", clusterId, meetingId);
                continue;
            }

            String featureId = voiceprintService.identifyFeatureId(slice);
            String displayName = voiceprintService.resolveDisplayName(featureId, meetingId);
            if (featureId == null || featureId.equals("speaker_unknown")) {
                processed++;
                continue;
            }

            for (TranscriptSegment seg : clusterSegs) {
                seg.setSpeakerId(featureId);
                if (displayName != null && !displayName.isBlank()) {
                    seg.setSpeakerName(displayName);
                }
                transcriptMapper.updateById(seg);
            }
            log.info("Offline voiceprint labeled cluster {} -> {} ({}) meeting {}",
                    clusterId, featureId, displayName, meetingId);
            processed++;
        }
    }

    private SliceWindow pickRepresentativeWindow(List<TranscriptSegment> sortedByDurationDesc) {
        int minMs = Math.max(1000, voiceprintProperties.getOfflineMinSliceMs());
        int maxMs = Math.max(minMs, voiceprintProperties.getOfflineMaxSliceMs());

        for (TranscriptSegment seg : sortedByDurationDesc) {
            int dur = segmentDurationMs(seg);
            if (dur >= minMs) {
                int start = seg.getStartTimeMs() != null ? seg.getStartTimeMs() : 0;
                int end = seg.getEndTimeMs() != null ? seg.getEndTimeMs() : start;
                return clampWindow(start, end, maxMs);
            }
        }

        List<TranscriptSegment> timeOrdered = new ArrayList<>(sortedByDurationDesc);
        timeOrdered.sort(Comparator.comparingInt(s -> s.getStartTimeMs() == null ? 0 : s.getStartTimeMs()));
        return mergeAdjacentSameSpeaker(timeOrdered, minMs, maxMs);
    }

    private SliceWindow mergeAdjacentSameSpeaker(List<TranscriptSegment> ordered, int minMs, int maxMs) {
        if (ordered.isEmpty()) {
            return null;
        }
        int start = ordered.get(0).getStartTimeMs() != null ? ordered.get(0).getStartTimeMs() : 0;
        int end = ordered.get(0).getEndTimeMs() != null ? ordered.get(0).getEndTimeMs() : start;
        for (int i = 1; i < ordered.size(); i++) {
            TranscriptSegment seg = ordered.get(i);
            int s = seg.getStartTimeMs() != null ? seg.getStartTimeMs() : end;
            int e = seg.getEndTimeMs() != null ? seg.getEndTimeMs() : s;
            end = Math.max(end, e);
            if (end - start >= minMs) {
                return clampWindow(start, end, maxMs);
            }
        }
        if (end > start) {
            return clampWindow(start, end, maxMs);
        }
        return null;
    }

    private static SliceWindow clampWindow(int startMs, int endMs, int maxMs) {
        int end = Math.max(endMs, startMs + 100);
        if (end - startMs > maxMs) {
            end = startMs + maxMs;
        }
        return new SliceWindow(startMs, end);
    }

    private static int segmentDurationMs(TranscriptSegment seg) {
        int start = seg.getStartTimeMs() != null ? seg.getStartTimeMs() : 0;
        int end = seg.getEndTimeMs() != null ? seg.getEndTimeMs() : start;
        return Math.max(0, end - start);
    }

    private record SliceWindow(int startMs, int endMs) {
    }
}
