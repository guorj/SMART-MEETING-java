package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingMinuteProperties;
import com.smartmeeting.config.MeetingTodoProperties;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.event.DomainEventPublisher;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.service.notification.MeetingFeishuNotifier;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MinuteGenerationService} 分步开关单元测试。
 */
@ExtendWith(MockitoExtension.class)
class MinuteGenerationServiceStepFlagsTest {

    private static final String MEETING_ID = "meet-step-flags";

    @Mock
    private VoiceprintService voiceprintService;
    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private ParticipantMapper participantMapper;
    @Mock
    private TranscriptMapper transcriptMapper;
    @Mock
    private FeishuService feishuService;
    @Mock
    private MeetingFeishuNotifier meetingFeishuNotifier;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private MinuteAIEnhancer minuteAIEnhancer;
    @Mock
    private MeetingMinuteService meetingMinuteService;
    @Mock
    private MeetingStateMachineService meetingStateMachineService;
    @Mock
    private DomainEventPublisher domainEventPublisher;
    @Mock
    private TranscriptSegmentHelper transcriptSegmentHelper;

    private MeetingMinuteProperties minuteProperties;
    private MeetingTodoProperties todoProperties;
    private MinuteGenerationService service;

    @BeforeEach
    void setUp() {
        minuteProperties = new MeetingMinuteProperties();
        minuteProperties.setAiEnhancementEnabled(false);
        minuteProperties.setPersistEnabled(false);
        todoProperties = new MeetingTodoProperties();
        todoProperties.setExtractionEnabled(false);

        service = new MinuteGenerationService(
                voiceprintService,
                meetingMapper,
                participantMapper,
                transcriptMapper,
                feishuService,
                meetingFeishuNotifier,
                restTemplate,
                new ObjectMapper(),
                minuteAIEnhancer,
                meetingMinuteService,
                minuteProperties,
                todoProperties,
                meetingStateMachineService,
                domainEventPublisher,
                transcriptSegmentHelper);
        ReflectionTestUtils.setField(service, "llmApiUrl", "https://api.example.com");
        ReflectionTestUtils.setField(service, "llmApiKey", "test-key");
        ReflectionTestUtils.setField(service, "llmModel", "test-model");
    }

    @Test
    @DisplayName("无 DB 分段时不内联调用离线校正，转写为空")
    void noDbSegments_usesEmptyTranscript() {
        minuteProperties.setLlmEnabled(false);
        minuteProperties.setFeishuDocEnabled(false);
        minuteProperties.setNotifyEnabled(false);
        stubMeetingAndTranscript(false, false, "");

        service.generateMinute(MEETING_ID, "/tmp/audio.pcm");

        verify(transcriptSegmentHelper, never()).listAllSegments(anyString());
    }

    @Test
    @DisplayName("有离线分段时从 DB 读取转写")
    void offlineSegments_readsFromDb() {
        minuteProperties.setLlmEnabled(false);
        minuteProperties.setFeishuDocEnabled(false);
        minuteProperties.setNotifyEnabled(false);
        stubMeetingAndTranscript(false, true, "offline transcript");

        service.generateMinute(MEETING_ID, null);

        verify(transcriptSegmentHelper).listAllSegments(MEETING_ID);
    }

    @Test
    @DisplayName("llm-enabled=false 时不调用 RestTemplate")
    void llmDisabled_skipsRestTemplate() {
        minuteProperties.setLlmEnabled(false);
        minuteProperties.setFeishuDocEnabled(false);
        minuteProperties.setNotifyEnabled(false);
        stubMeetingAndTranscript(false, false, "");

        service.generateMinute(MEETING_ID, null);

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(com.fasterxml.jackson.databind.JsonNode.class));
    }

    @Test
    @DisplayName("feishu-doc-enabled=false 时不创建飞书文档")
    void feishuDocDisabled_skipsCreateDoc() {
        minuteProperties.setLlmEnabled(false);
        minuteProperties.setFeishuDocEnabled(false);
        minuteProperties.setNotifyEnabled(false);
        stubMeetingAndTranscript(true, false, "hello transcript");

        service.generateMinute(MEETING_ID, null);

        verify(feishuService, never()).createDoc(anyString(), anyString());
        verify(feishuService, never()).updateDoc(anyString(), anyString());
    }

    @Test
    @DisplayName("notify-enabled=false 时不推送飞书通知")
    void notifyDisabled_skipsNotifier() {
        minuteProperties.setLlmEnabled(false);
        minuteProperties.setFeishuDocEnabled(false);
        minuteProperties.setNotifyEnabled(false);
        Meeting meeting = stubMeetingAndTranscript(true, false, "hello");
        meeting.setChatId("chat-1");

        service.generateMinute(MEETING_ID, null);

        verify(meetingFeishuNotifier, never()).sendCardMessage(anyString(), anyString(), anyList(), anyString(), anyString(), anyString());
        verify(meetingFeishuNotifier, never()).sendTextMessage(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private Meeting stubMeetingAndTranscript(boolean hasRealtimeSegments, boolean hasOfflineSegments, String transcript) {
        Meeting meeting = new Meeting();
        meeting.setId(MEETING_ID);
        meeting.setTitle("测试会议");
        meeting.setCompany("测试公司");
        meeting.setGroupName("测试组");
        meeting.setStatus(MeetingStatus.PROCESSING.name());
        meeting.setCreatorId("user-1");
        meeting.setCreatedAt(LocalDateTime.now());
        meeting.setUpdatedAt(LocalDateTime.now());
        when(meetingMapper.selectById(MEETING_ID)).thenReturn(meeting);
        when(participantMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(transcriptSegmentHelper.hasFinalRealtimeSegments(MEETING_ID)).thenReturn(hasRealtimeSegments);
        if (hasRealtimeSegments) {
            when(transcriptSegmentHelper.listFinalSegments(MEETING_ID)).thenReturn(List.of());
            when(transcriptSegmentHelper.buildLabeledTranscriptText(anyList())).thenReturn(transcript);
        } else {
            when(transcriptSegmentHelper.hasAnySegments(MEETING_ID)).thenReturn(hasOfflineSegments);
            if (hasOfflineSegments) {
                when(transcriptSegmentHelper.listAllSegments(MEETING_ID)).thenReturn(List.of());
                when(transcriptSegmentHelper.buildLabeledTranscriptText(anyList())).thenReturn(transcript);
            }
        }
        return meeting;
    }
}
