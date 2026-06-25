package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 快速开始协调器：仅建草稿、每模板幂等复用、主持页才真正 STARTED。
 */
@ExtendWith(MockitoExtension.class)
class FeishuMeetingStartCoordinatorTest {

    @Mock
    private FeishuStartMeetingPendingStore startMeetingPendingStore;
    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private MeetingService meetingService;
    @Mock
    private FeishuService feishuService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private FeishuCardBuilder cardBuilder;
    @Mock
    private MeetingWebPageUrls meetingWebPageUrls;
    @Mock
    private ParticipantLinkService participantLinkService;
    @Mock
    private MeetingPreStageService meetingPreStageService;
    @Mock
    private PresetScheduleTimeResolver presetScheduleTimeResolver;

    @InjectMocks
    private FeishuMeetingStartCoordinator coordinator;

    private MeetingCreateRequest request;

    @BeforeEach
    void setUp() {
        request = new MeetingCreateRequest();
        request.setPresetTypeCode(8);
        when(feishuService.getUserNameByUserId("ou_alan")).thenReturn("Alan");
        when(jwtUtil.generateOperatorMeetingToken(any(), any(), any(), any())).thenReturn("tok-8");
        when(meetingWebPageUrls.recordingPageUrlWithAutostart(any(), any())).thenReturn("https://host/rec/m-8?token=tok-8&autostart=1");
        when(cardBuilder.buildMeetingCreatedNotifyCard(any(), any(), any())).thenReturn("{}");
        when(feishuService.sendInteractiveCardToUserId(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("新建快速开始不调用 startMeeting，状态保持草稿")
    void create_doesNotStartMeetingImmediately() {
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        MeetingResponse created = new MeetingResponse();
        created.setId("m-new");
        created.setTitle("会务类型8");
        created.setPresetTypeCode(8);
        created.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingService.createMeeting(any())).thenReturn(created);
        MeetingResponse full = new MeetingResponse();
        full.setId("m-new");
        full.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingService.getMeeting("m-new")).thenReturn(full);

        MeetingResponse out = coordinator.createMeetingStartAndNotifyFeishu("ou_alan", "oc_chat", request);

        verify(meetingService).createMeeting(any());
        verify(meetingService, never()).startMeeting(any());
        verify(meetingPreStageService).runPreStage("m-new", 8);
        assertEquals(MeetingStatus.ISSUE_COLLECTING.name(), out.getStatus());
    }

    @Test
    @DisplayName("同模板已有草稿时复用，不新建")
    void create_reusesExistingDraftPerPreset() {
        Meeting draft = new Meeting();
        draft.setId("m-draft-8");
        draft.setTitle("会务类型8");
        draft.setPresetTypeCode(8);
        draft.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(draft);
        when(meetingMapper.selectById("m-draft-8")).thenReturn(draft);
        when(meetingWebPageUrls.resolveRecordingPageUrl(any(), any(), any()))
                .thenReturn("https://host/rec/m-draft-8?token=tok-8");
        MeetingResponse existing = new MeetingResponse();
        existing.setId("m-draft-8");
        existing.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingService.getMeeting("m-draft-8")).thenReturn(existing);

        MeetingResponse out = coordinator.createMeetingStartAndNotifyFeishu("ou_alan", "oc_chat", request);

        verify(meetingService, never()).createMeeting(any());
        verify(meetingService, never()).startMeeting(any());
        assertEquals("m-draft-8", out.getId());
        verify(cardBuilder).buildMeetingCreatedNotifyCard(eq("m-draft-8"), any(), any());
    }

    @Test
    @DisplayName("模板 99 已有草稿时仍新建，不复用")
    void create_preset99AlwaysCreatesNewDraft() {
        request.setPresetTypeCode(99);
        when(meetingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        MeetingResponse created = new MeetingResponse();
        created.setId("m-new-99");
        created.setTitle("测试会议");
        created.setPresetTypeCode(99);
        created.setStatus(MeetingStatus.ISSUE_COLLECTING.name());
        when(meetingService.createMeeting(any())).thenReturn(created);
        when(meetingService.getMeeting("m-new-99")).thenReturn(created);

        MeetingResponse out = coordinator.createMeetingStartAndNotifyFeishu("ou_alan", "oc_chat", request);

        verify(meetingService).createMeeting(any());
        assertEquals("m-new-99", out.getId());
    }
}
