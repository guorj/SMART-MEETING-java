package com.smartmeeting.service;

import com.smartmeeting.api.dto.internal.VoiceprintRelabelResult;
import com.smartmeeting.config.MeetingVoiceprintProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineVoiceprintRelabelServiceTest {

    private static final String MEETING_ID = "65aa5af5-5633-46c6-86da-b9ecd7cd22a5";

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private MeetingAudioMaterializerService meetingAudioMaterializerService;
    @Mock
    private TranscriptSegmentHelper transcriptSegmentHelper;
    @Mock
    private OfflineSpeakerLabeler offlineSpeakerLabeler;
    @Mock
    private VoiceprintService voiceprintService;

    @TempDir
    Path tempDir;

    private MeetingVoiceprintProperties voiceprintProperties;
    private OfflineVoiceprintRelabelService service;

    @BeforeEach
    void setUp() {
        voiceprintProperties = new MeetingVoiceprintProperties();
        voiceprintProperties.setOfflineLabelEnabled(true);
        service = new OfflineVoiceprintRelabelService(
                meetingMapper,
                meetingAudioMaterializerService,
                transcriptSegmentHelper,
                voiceprintProperties,
                offlineSpeakerLabeler,
                voiceprintService);
    }

    @Test
    void relabel_success_updatesCount() throws Exception {
        Path pcm = tempDir.resolve("meeting.pcm");
        Files.write(pcm, new byte[]{0, 0, 1, 0});

        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        meeting.setAudioPath(pcm.toString());

        TranscriptSegment before = segment("s1", "speaker_1", null);
        TranscriptSegment after = segment("s1", "feat-new", "张三");

        when(meetingMapper.selectById(MEETING_ID)).thenReturn(meeting);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);
        when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(true);
        when(meetingAudioMaterializerService.materialize(eq(MEETING_ID), eq(pcm.toString()), isNull()))
                .thenReturn(pcm.toString());
        when(transcriptSegmentHelper.listAllSegments(MEETING_ID))
                .thenReturn(List.of(before))
                .thenReturn(List.of(after));

        VoiceprintRelabelResult result = service.relabel(MEETING_ID);

        assertThat(result.getSegmentCount()).isEqualTo(1);
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getAudioPathUsed()).isEqualTo(pcm.toString());
        verify(offlineSpeakerLabeler).label(eq(MEETING_ID), eq(pcm.toString()), eq(List.of(before)), eq(List.of()));
        verify(voiceprintService).refreshVoiceprintCache();
    }

    @Test
    void relabel_rejectsRealtimeMeeting() {
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(new Meeting());
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.relabel(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定稿转写");
    }

    @Test
    void relabel_rejectsNoSegments() {
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(new Meeting());
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);
        when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.relabel(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无转写分段");
    }

    @Test
    void relabel_rejectsMissingPcm() {
        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(meeting);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);
        when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(true);
        when(meetingAudioMaterializerService.materialize(anyString(), any(), any())).thenReturn("");

        assertThatThrownBy(() -> service.relabel(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PCM");
    }

    @Test
    void relabel_rejectsWhenOfflineLabelDisabled() {
        voiceprintProperties.setOfflineLabelEnabled(false);
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(new Meeting());

        assertThatThrownBy(() -> service.relabel(MEETING_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("offline-label-enabled");
    }

    private static TranscriptSegment segment(String id, String speakerId, String speakerName) {
        TranscriptSegment seg = new TranscriptSegment();
        seg.setId(id);
        seg.setMeetingId(MEETING_ID);
        seg.setSpeakerId(speakerId);
        seg.setSpeakerName(speakerName);
        seg.setStartTimeMs(0);
        seg.setEndTimeMs(5000);
        seg.setText("测试");
        return seg;
    }
}
