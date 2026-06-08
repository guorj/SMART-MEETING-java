package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.event.MeetingEndedEvent;
import com.smartmeeting.event.OfflineAsrRequestedEvent;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostMeetingOrchestratorTest {

    private static final String MEETING_ID = "meet-orchestrator";

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private TranscriptSegmentHelper transcriptSegmentHelper;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private MeetingStateMachineService meetingStateMachineService;
    @Mock
    private OfflineAsrService offlineAsrService;
    @Mock
    private MinuteGenerationService minuteGenerationService;

    private MeetingAsrProperties asrProperties;
    private MeetingMinuteProperties minuteProperties;
    private PostMeetingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        asrProperties = new MeetingAsrProperties();
        minuteProperties = new MeetingMinuteProperties();
        orchestrator = new PostMeetingOrchestrator(
                meetingMapper,
                asrProperties,
                minuteProperties,
                transcriptSegmentHelper,
                domainEventPublisher,
                meetingStateMachineService,
                offlineAsrService,
                minuteGenerationService);
    }

    @Test
    @DisplayName("offline=true minute=false 时发布离线事件并保持 PROCESSING")
    void offlineOnly_queuesOfflineAsr() {
        asrProperties.setOfflineEnabled(true);
        minuteProperties.setGenerationEnabled(false);
        stubMeeting("/data/audio/a.pcm", null);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);

        String status = orchestrator.dispatchAfterMeetingEnded(MEETING_ID, "/data/audio/a.pcm", null, null);

        assertThat(status).isEqualTo(MeetingStatus.PROCESSING.name());
        verify(domainEventPublisher).publish(any(OfflineAsrRequestedEvent.class));
        verify(domainEventPublisher, never()).publish(any(MeetingEndedEvent.class));
        verify(meetingStateMachineService, never()).apply(eq(MEETING_ID), any());
    }

    @Test
    @DisplayName("offline=false minute=true 时直接发布纪要事件")
    void minuteOnly_queuesMinute() {
        asrProperties.setOfflineEnabled(false);
        minuteProperties.setGenerationEnabled(true);
        stubMeeting(null, null);

        String status = orchestrator.dispatchAfterMeetingEnded(MEETING_ID, null, null, null);

        assertThat(status).isEqualTo(MeetingStatus.PROCESSING.name());
        verify(domainEventPublisher).publish(any(MeetingEndedEvent.class));
        verify(domainEventPublisher, never()).publish(any(OfflineAsrRequestedEvent.class));
    }

    @Test
    @DisplayName("offline=true minute=true 时先发布离线事件")
    void bothEnabled_queuesOfflineFirst() {
        asrProperties.setOfflineEnabled(true);
        minuteProperties.setGenerationEnabled(true);
        stubMeeting("/data/audio/a.pcm", null);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);

        orchestrator.dispatchAfterMeetingEnded(MEETING_ID, "/data/audio/a.pcm", null, null);

        verify(domainEventPublisher).publish(any(OfflineAsrRequestedEvent.class));
        verify(domainEventPublisher, never()).publish(any(MeetingEndedEvent.class));
    }

    @Test
    @DisplayName("两者皆关时直接 COMPLETED")
    void bothDisabled_completesMeeting() {
        asrProperties.setOfflineEnabled(false);
        minuteProperties.setGenerationEnabled(false);
        Meeting meeting = stubMeeting(null, null);

        String status = orchestrator.dispatchAfterMeetingEnded(MEETING_ID, null, null, null);

        assertThat(status).isEqualTo(MeetingStatus.COMPLETED.name());
        verify(domainEventPublisher, never()).publish(any());
        verify(meetingStateMachineService).apply(eq(MEETING_ID), any());
        verify(meetingMapper).updateById(meeting);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.COMPLETED.name());
    }

    @Test
    @DisplayName("有实时分段时跳过离线，minute=true 直接纪要")
    void realtimeSegments_skipOffline() {
        asrProperties.setOfflineEnabled(true);
        minuteProperties.setGenerationEnabled(true);
        stubMeeting("/data/audio/a.pcm", null);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(true);

        orchestrator.dispatchAfterMeetingEnded(MEETING_ID, "/data/audio/a.pcm", null, null);

        verify(domainEventPublisher).publish(any(MeetingEndedEvent.class));
        verify(domainEventPublisher, never()).publish(any(OfflineAsrRequestedEvent.class));
    }

    @Test
    @DisplayName("手动重生成：需离线时先同步离线再纪要")
    void regenerate_runsOfflineThenMinute() {
        asrProperties.setOfflineEnabled(true);
        stubMeeting("/data/audio/a.pcm", null);
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(false);

        orchestrator.triggerRegenerateMinutes(MEETING_ID);

        verify(offlineAsrService).runOfflineAsrSync(MEETING_ID, "/data/audio/a.pcm");
        verify(minuteGenerationService).generateMinute(MEETING_ID, "/data/audio/a.pcm");
    }

    private Meeting stubMeeting(String audioPath, String sourceUrl) {
        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        meeting.setStatus(MeetingStatus.PROCESSING.name());
        meeting.setAudioPath(audioPath);
        meeting.setSourceAudioUrl(sourceUrl);
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(meeting);
        return meeting;
    }
}
