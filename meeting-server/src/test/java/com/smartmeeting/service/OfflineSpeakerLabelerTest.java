package com.smartmeeting.service;

import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.config.MeetingIsvProperties;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.repository.TranscriptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineSpeakerLabelerTest {

    @Mock
    private TranscriptMapper transcriptMapper;
    @Mock
    private VoiceprintService voiceprintService;

    @TempDir
    Path tempDir;

    private MeetingVoiceprintProperties props;
    private OfflineSpeakerLabeler labeler;

    @BeforeEach
    void setUp() {
        props = new MeetingVoiceprintProperties();
        props.setOfflineMinSliceMs(3000);
        props.setOfflineMaxSliceMs(10000);
        props.setOfflineVoteSlices(3);
        MeetingIsvProperties isvProps = new MeetingIsvProperties();
        isvProps.setSearchTopKMax(10);
        isvProps.setSearchTopKMin(3);
        isvProps.setMinSliceBytes(1600);
        isvProps.setMinSegmentMsForSlice(500);
        labeler = new OfflineSpeakerLabeler(transcriptMapper, voiceprintService, props, isvProps);
    }

    @Test
    @DisplayName("簇级投票成功时整簇写入 featureId")
    void clusterVote_labelsWholeCluster() throws Exception {
        Path pcm = tempDir.resolve("test.pcm");
        Files.write(pcm, new byte[32000 * 10]);

        TranscriptSegment seg = segment("speaker_1", 0, 5000, "hello");
        when(voiceprintService.identifyAmongCandidates(any(), isNull(), eq(10)))
                .thenReturn(Optional.of(new XfyunIsvClient.IdentifyResult("feat-a", 0.8)));
        when(voiceprintService.resolveDisplayName("feat-a", "m1")).thenReturn("Alice");

        labeler.label("m1", pcm.toString(), List.of(seg), List.of("feat-a"));

        verify(transcriptMapper).updateById(argThat(s ->
                "feat-a".equals(s.getSpeakerId()) && "Alice".equals(s.getSpeakerName())));
    }

    @Test
    @DisplayName("ISV topK 使用 search-top-k-max 配置")
    void identifySegment_usesConfiguredTopK() throws Exception {
        props.setOfflineSegmentRelabelEnabled(false);
        props.setOfflineSplitClusterEnabled(false);
        Path pcm = tempDir.resolve("topk.pcm");
        Files.write(pcm, new byte[32000 * 10]);
        TranscriptSegment seg = segment("speaker_1", 0, 5000, "hello");
        when(voiceprintService.identifyAmongCandidates(any(), isNull(), eq(10)))
                .thenReturn(Optional.empty());

        labeler.label("m1", pcm.toString(), List.of(seg), List.of("feat-a", "feat-b"));

        verify(voiceprintService).identifyAmongCandidates(any(), isNull(), eq(10));
    }

    @Test
    @DisplayName("isStillIstClusterId 识别 speaker_N")
    void istClusterIdDetection() {
        assertThat(OfflineSpeakerLabeler.isStillIstClusterId("speaker_3")).isTrue();
        assertThat(OfflineSpeakerLabeler.isStillIstClusterId("feat-x")).isFalse();
    }

    private static TranscriptSegment segment(String speakerId, int start, int end, String text) {
        TranscriptSegment seg = new TranscriptSegment();
        seg.setId(UUID.randomUUID().toString());
        seg.setMeetingId("m1");
        seg.setSpeakerId(speakerId);
        seg.setStartTimeMs(start);
        seg.setEndTimeMs(end);
        seg.setText(text);
        seg.setIsFinal(true);
        return seg;
    }
}
