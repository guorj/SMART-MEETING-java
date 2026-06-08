package com.smartmeeting.service;

import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.model.OfflineAsrMessage;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfflineAsrServiceTest {

    private static final String MEETING_ID = "meet-offline-svc";

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private MeetingAudioMaterializerService meetingAudioMaterializerService;
    @Mock
    private OfflineCorrectionService correctionService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private MeetingStateMachineService meetingStateMachineService;
    @Mock
    private TranscriptSegmentHelper transcriptSegmentHelper;

    private MeetingMinuteProperties minuteProperties;
    private OfflineAsrService service;

    @BeforeEach
    void setUp() {
        minuteProperties = new MeetingMinuteProperties();
        service = new OfflineAsrService(
                meetingMapper,
                meetingAudioMaterializerService,
                correctionService,
                minuteProperties,
                domainEventPublisher,
                meetingStateMachineService,
                transcriptSegmentHelper);
    }

    @Test
    @DisplayName("离线完成后 minute=true 时发布 MeetingEndedEvent")
    void afterOffline_minuteEnabled_publishesMinuteEvent() {
        minuteProperties.setGenerationEnabled(true);
        Meeting meeting = stubMeeting();
        when(meetingAudioMaterializerService.materialize(eq(MEETING_ID), any(), any()))
                .thenReturn("/tmp/a.pcm");

        OfflineAsrMessage message = OfflineAsrMessage.builder()
                .meetingId(MEETING_ID)
                .audioPath("/tmp/a.pcm")
                .featureIds(List.of("f1"))
                .sentAt(1L)
                .build();
        service.process(message);

        verify(correctionService).correct(MEETING_ID, "/tmp/a.pcm");
        verify(domainEventPublisher).publish(any(MeetingEndedEvent.class));
        verify(meetingStateMachineService, never()).apply(eq(MEETING_ID), any());
        verify(meetingMapper, never()).updateById(meeting);
    }

    @Test
    @DisplayName("离线完成后 minute=false 且有分段时标记 COMPLETED")
    void afterOffline_minuteDisabled_completesMeeting() {
        minuteProperties.setGenerationEnabled(false);
        Meeting meeting = stubMeeting();
        when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(true);
        when(meetingAudioMaterializerService.materialize(eq(MEETING_ID), any(), any()))
                .thenReturn("/tmp/a.pcm");

        service.process(OfflineAsrMessage.builder()
                .meetingId(MEETING_ID)
                .audioPath("/tmp/a.pcm")
                .sentAt(1L)
                .build());

        verify(domainEventPublisher, never()).publish(any(MeetingEndedEvent.class));
        verify(meetingStateMachineService).apply(eq(MEETING_ID), any());
        verify(meetingMapper).updateById(meeting);
    }

    @Test
    @DisplayName("离线无分段时保持 PROCESSING 不标 COMPLETED")
    void afterOffline_noSegments_keepsProcessing() {
        minuteProperties.setGenerationEnabled(false);
        stubMeeting();
        when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(false);
        when(meetingAudioMaterializerService.materialize(eq(MEETING_ID), any(), any()))
                .thenReturn("/tmp/a.pcm");

        service.process(OfflineAsrMessage.builder()
                .meetingId(MEETING_ID)
                .audioPath("/tmp/a.pcm")
                .sentAt(1L)
                .build());

        verify(meetingStateMachineService, never()).apply(eq(MEETING_ID), any());
        verify(meetingMapper, never()).updateById(any());
    }

    private Meeting stubMeeting() {
        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        meeting.setStatus(MeetingStatus.PROCESSING.name());
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(meeting);
        return meeting;
    }
}
