package com.smartmeeting.service;

import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.config.MeetingIsvProperties;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.config.system.ConfigValueClamp;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.util.PcmSliceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线转写 IST 簇 → ISV 姓名标注：簇级多切片投票、未命名簇按段补标、簇内分裂。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineSpeakerLabeler {

    private final TranscriptMapper transcriptMapper;
    private final VoiceprintService voiceprintService;
    private final MeetingVoiceprintProperties voiceprintProperties;
    private final MeetingIsvProperties isvProperties;

    /**
     * 对已有 IST 分段做声纹标注并回写数据库。
     *
     * @param candidateFeatureIds 仅 IST 阶段使用；ISV 1:N 为全库匹配，不按参会人过滤
     */
    public void label(String meetingId, String audioPath, List<TranscriptSegment> segments,
                      List<String> candidateFeatureIds) {
        Map<String, List<TranscriptSegment>> byCluster = segments.stream()
                .filter(s -> s.getSpeakerId() != null && !s.getSpeakerId().isBlank())
                .collect(Collectors.groupingBy(TranscriptSegment::getSpeakerId, LinkedHashMap::new, Collectors.toList()));

        int processedClusters = 0;
        Set<String> unnamedClusters = new LinkedHashSet<>();

        for (Map.Entry<String, List<TranscriptSegment>> entry : byCluster.entrySet()) {
            if (processedClusters >= voiceprintProperties.getOfflineMaxSpeakers()) {
                log.warn("Offline voiceprint speaker cap reached for meeting {}", meetingId);
                break;
            }
            String clusterId = entry.getKey();
            List<TranscriptSegment> clusterSegs = entry.getValue();
            boolean named = tryClusterVote(meetingId, audioPath, clusterId, clusterSegs);
            processedClusters++;
            if (!named) {
                unnamedClusters.add(clusterId);
            }
        }

        if (voiceprintProperties.isOfflineSegmentRelabelEnabled()) {
            for (String clusterId : unnamedClusters) {
                relabelSegmentsIndividually(meetingId, audioPath, byCluster.get(clusterId));
            }
        }

        if (voiceprintProperties.isOfflineSplitClusterEnabled()) {
            for (String clusterId : unnamedClusters) {
                applyClusterSplitOrWholeName(meetingId, clusterId, byCluster.get(clusterId));
            }
        }
    }

    private boolean tryClusterVote(String meetingId, String audioPath, String clusterId,
                                   List<TranscriptSegment> clusterSegs) {
        List<TranscriptSegment> byDuration = new ArrayList<>(clusterSegs);
        byDuration.sort(Comparator.comparingInt(s -> -segmentDurationMs(s)));

        int voteSlices = ConfigValueClamp.atLeast(1, voiceprintProperties.getOfflineVoteSlices());
        Map<String, List<Double>> votes = new HashMap<>();

        int attempted = 0;
        for (TranscriptSegment seg : byDuration) {
            if (attempted >= voteSlices) {
                break;
            }
            Optional<XfyunIsvClient.IdentifyResult> match = identifySegment(meetingId, audioPath, seg);
            if (match.isEmpty()) {
                continue;
            }
            attempted++;
            votes.computeIfAbsent(match.get().featureId(), k -> new ArrayList<>()).add(match.get().score());
        }

        if (votes.isEmpty()) {
            return false;
        }

        String winner = null;
        double winnerAvg = 0;
        int required = (int) Math.ceil(Math.max(1, attempted) / 2.0);

        for (Map.Entry<String, List<Double>> voteEntry : votes.entrySet()) {
            if (voteEntry.getValue().size() < required) {
                continue;
            }
            double avg = voteEntry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (winner == null || voteEntry.getValue().size() > votes.get(winner).size()
                    || (voteEntry.getValue().size() == votes.get(winner).size() && avg > winnerAvg)) {
                winner = voteEntry.getKey();
                winnerAvg = avg;
            }
        }

        if (winner == null) {
            return false;
        }

        String displayName = voiceprintService.resolveDisplayName(winner, meetingId);
        for (TranscriptSegment seg : clusterSegs) {
            seg.setSpeakerId(winner);
            if (displayName != null && !displayName.isBlank()) {
                seg.setSpeakerName(displayName);
            }
            transcriptMapper.updateById(seg);
        }
        log.info("Offline voiceprint vote labeled cluster {} -> {} ({}) meeting {}, votes={}, avgScore={}",
                clusterId, winner, displayName, meetingId, votes.get(winner).size(), winnerAvg);
        return true;
    }

    private void relabelSegmentsIndividually(String meetingId, String audioPath,
                                             List<TranscriptSegment> clusterSegs) {
        if (clusterSegs == null || clusterSegs.isEmpty()) {
            return;
        }
        int minMs = effectiveMinSliceMs();
        for (TranscriptSegment seg : clusterSegs) {
            if (!isStillIstClusterId(seg.getSpeakerId())) {
                continue;
            }
            if (segmentDurationMs(seg) < minMs) {
                continue;
            }
            Optional<XfyunIsvClient.IdentifyResult> match = identifySegment(meetingId, audioPath, seg);
            if (match.isEmpty()) {
                continue;
            }
            String featureId = match.get().featureId();
            String displayName = voiceprintService.resolveDisplayName(featureId, meetingId);
            seg.setSpeakerId(featureId);
            if (displayName != null && !displayName.isBlank()) {
                seg.setSpeakerName(displayName);
            }
            transcriptMapper.updateById(seg);
        }
    }

    private void applyClusterSplitOrWholeName(String meetingId, String clusterId, List<TranscriptSegment> clusterSegs) {
        if (clusterSegs == null || clusterSegs.isEmpty()) {
            return;
        }
        int minSegs = ConfigValueClamp.atLeast(1, voiceprintProperties.getOfflineSplitMinSegments());
        Map<String, List<TranscriptSegment>> byFeature = new LinkedHashMap<>();
        List<TranscriptSegment> unlabeled = new ArrayList<>();

        for (TranscriptSegment seg : clusterSegs) {
            String sid = seg.getSpeakerId();
            if (sid == null || sid.isBlank() || isStillIstClusterId(sid)) {
                unlabeled.add(seg);
                continue;
            }
            byFeature.computeIfAbsent(sid, k -> new ArrayList<>()).add(seg);
        }

        List<Map.Entry<String, List<TranscriptSegment>>> significant = byFeature.entrySet().stream()
                .filter(e -> e.getValue().size() >= minSegs)
                .toList();

        if (significant.size() == 1 && !unlabeled.isEmpty()) {
            String featureId = significant.get(0).getKey();
            String displayName = voiceprintService.resolveDisplayName(featureId, meetingId);
            for (TranscriptSegment seg : unlabeled) {
                seg.setSpeakerId(featureId);
                if (displayName != null && !displayName.isBlank()) {
                    seg.setSpeakerName(displayName);
                }
                transcriptMapper.updateById(seg);
            }
            log.info("Offline voiceprint whole-cluster name {} -> {} ({}) meeting {}, unlabeled={}",
                    clusterId, featureId, displayName, meetingId, unlabeled.size());
        } else if (significant.size() >= 2) {
            log.info("Offline voiceprint split cluster {} into {} features meeting {}",
                    clusterId, significant.size(), meetingId);
        }
    }

    private Optional<XfyunIsvClient.IdentifyResult> identifySegment(String meetingId, String audioPath,
                                                                    TranscriptSegment seg) {
        SliceWindow window = sliceWindowForSegment(seg);
        if (window == null) {
            return Optional.empty();
        }
        byte[] slice;
        try {
            slice = PcmSliceUtil.slice(audioPath, window.startMs, window.endMs, PcmSliceUtil.DEFAULT_SAMPLE_RATE);
        } catch (IOException e) {
            log.warn("PCM slice failed for {}: {}", meetingId, e.getMessage());
            return Optional.empty();
        }
        if (slice.length < isvProperties.getMinSliceBytes()) {
            return Optional.empty();
        }
        int topK = ConfigValueClamp.effectiveTopK(
                isvProperties.getSearchTopKMax(),
                isvProperties.getSearchTopKMin(),
                isvProperties.getSearchTopKMax());
        return voiceprintService.identifyAmongCandidates(slice, null, topK);
    }

    private int effectiveMinSliceMs() {
        return Math.max(voiceprintProperties.getOfflineMinSliceFloorMs(),
                voiceprintProperties.getOfflineMinSliceMs());
    }

    private SliceWindow sliceWindowForSegment(TranscriptSegment seg) {
        int minMs = effectiveMinSliceMs();
        int maxMs = Math.max(minMs, voiceprintProperties.getOfflineMaxSliceMs());
        int dur = segmentDurationMs(seg);
        if (dur < isvProperties.getMinSegmentMsForSlice()) {
            return null;
        }
        int start = seg.getStartTimeMs() != null ? seg.getStartTimeMs() : 0;
        int end = seg.getEndTimeMs() != null ? seg.getEndTimeMs() : start;
        return clampWindow(start, end, maxMs, minMs);
    }

    static boolean isStillIstClusterId(String speakerId) {
        return speakerId != null && speakerId.startsWith("speaker_");
    }

    static SliceWindow clampWindow(int startMs, int endMs, int maxMs, int minMs) {
        int end = Math.max(endMs, startMs + 100);
        if (end - startMs > maxMs) {
            end = startMs + maxMs;
        }
        if (end - startMs < minMs && endMs > startMs) {
            end = Math.min(startMs + minMs, endMs);
        }
        if (end <= startMs) {
            return null;
        }
        return new SliceWindow(startMs, end);
    }

    static int segmentDurationMs(TranscriptSegment seg) {
        int start = seg.getStartTimeMs() != null ? seg.getStartTimeMs() : 0;
        int end = seg.getEndTimeMs() != null ? seg.getEndTimeMs() : start;
        return Math.max(0, end - start);
    }

    record SliceWindow(int startMs, int endMs) {
    }
}
