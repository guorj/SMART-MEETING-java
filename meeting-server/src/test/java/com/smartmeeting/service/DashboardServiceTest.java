package com.smartmeeting.service;

import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.UserMappingMapper;
import com.smartmeeting.repository.VoiceprintMapper;
import com.smartmeeting.config.MeetingVoiceprintLifecycleProperties;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private UserMappingMapper userMappingMapper;
    @Mock private VoiceprintMapper voiceprintMapper;
    @Mock private MeetingMapper meetingMapper;
    @Mock private ParticipantMapper participantMapper;
    @Mock private MeetingTypePresetService presetService;
    @Mock private FeishuMeetingStartCoordinator coordinator;
    @Mock private FeishuStartMeetingPendingStore pendingStore;
    @Mock private FeishuService feishuService;
    @Mock private VoiceprintRegisterService voiceprintRegisterService;
    @Mock private MeetingService meetingService;
    @Mock private MeetingCalendarSyncService meetingCalendarSyncService;
    @Mock private JwtUtil jwtUtil;
    @Mock private MeetingVoiceprintLifecycleProperties lifecycleProperties;
    @Mock private com.smartmeeting.util.MeetingWebPageUrls meetingWebPageUrls;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                userMappingMapper,
                voiceprintMapper,
                meetingMapper,
                participantMapper,
                presetService,
                coordinator,
                pendingStore,
                feishuService,
                voiceprintRegisterService,
                meetingService,
                meetingCalendarSyncService,
                jwtUtil,
                lifecycleProperties,
                meetingWebPageUrls
        );
    }

    @Test
    void getUserInfo_shouldMergeMappingAndVoiceprint() {
        UserMapping mapping = new UserMapping();
        mapping.setUserId(7);
        mapping.setUserName("Alice");
        mapping.setFeishuUserId("116afd4c");
        when(userMappingMapper.selectOne(any())).thenReturn(mapping);

        Voiceprint vp = new Voiceprint();
        vp.setFeatureId("fp_1");
        vp.setExpiresAt(LocalDateTime.now().plusDays(10));
        when(voiceprintMapper.selectOne(any())).thenReturn(vp);

        DashboardService.UserInfo info = dashboardService.getUserInfo("116afd4c", null);
        assertTrue(info.isMappingExists());
        assertTrue(info.isVoiceprintRegistered());
        assertEquals("Alice", info.getUserName());
        assertEquals("fp_1", info.getVoiceprintFeatureId());
    }

    @Test
    void getVoiceprintStatus_shouldReturnUnregisteredWhenMissing() {
        when(voiceprintMapper.selectOne(any())).thenReturn(null);
        DashboardService.VoiceprintStatusResult result = dashboardService.getVoiceprintStatus("uid_2");
        assertFalse(result.isRegistered());
        assertEquals("NONE", result.getExpiryStatus());
    }

    @Test
    void getRecentMeetings_shouldFallbackToCreatorMeetings() {
        when(participantMapper.selectList(any())).thenReturn(List.of());
        Meeting meeting = new Meeting();
        meeting.setId("m1");
        meeting.setTitle("Weekly");
        meeting.setStatus("COMPLETED");
        meeting.setCreatedAt(LocalDateTime.now());
        when(meetingMapper.selectList(any())).thenReturn(List.of(meeting));

        List<DashboardService.MeetingSummary> list = dashboardService.getRecentMeetings("feishu_user_id_3", 10);
        assertEquals(1, list.size());
        assertEquals("m1", list.get(0).getId());
    }

    @Test
    void getRecentMeetings_shouldUnionCreatorAndParticipantMeetings() {
        Participant participant = new Participant();
        participant.setMeetingId("m-participant");
        when(participantMapper.selectList(any())).thenReturn(List.of(participant));

        Meeting creatorOnly = new Meeting();
        creatorOnly.setId("m-creator");
        creatorOnly.setTitle("Creator draft");
        creatorOnly.setStatus("ISSUE_COLLECTING");
        creatorOnly.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        Meeting asParticipant = new Meeting();
        asParticipant.setId("m-participant");
        asParticipant.setTitle("Old joined");
        asParticipant.setStatus("COMPLETED");
        asParticipant.setCreatedAt(LocalDateTime.now().minusHours(2));

        when(meetingMapper.selectList(any())).thenReturn(List.of(creatorOnly, asParticipant));

        List<DashboardService.MeetingSummary> list = dashboardService.getRecentMeetings("feishu_user_id_4", 10);
        assertEquals(2, list.size());
        assertEquals("m-creator", list.get(0).getId());
    }

    @Test
    void getMeetingPresets_shouldAppendOtherType() {
        MeetingPresetResponse preset = MeetingPresetResponse.builder().code(1).displayName("A").build();
        when(presetService.listPresets()).thenReturn(List.of(preset));
        List<MeetingPresetResponse> list = dashboardService.getMeetingPresets();
        assertEquals(1, list.size());
        assertEquals(1, list.get(0).getCode());
    }
}
