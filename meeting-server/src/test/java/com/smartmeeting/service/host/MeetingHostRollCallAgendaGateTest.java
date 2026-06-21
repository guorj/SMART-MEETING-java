package com.smartmeeting.service.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.config.MeetingHostWebSocketHandler;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.config.MeetingHostRuntimeProperties;
import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.config.MeetingRuntimeConfig;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.service.MeetingService;
import com.smartmeeting.service.PresetAgendaDocService;
import com.smartmeeting.tts.XfyunOnlineTtsSynthesizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 主持会话：议程无检点会序时不自动/不可手动启动检点。
 */
@ExtendWith(MockitoExtension.class)
class MeetingHostRollCallAgendaGateTest {

    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private ParticipantMapper participantMapper;
    @Mock
    private MeetingTypePresetMapper presetMapper;
    @Mock
    private PresetAgendaDocService presetAgendaDocService;
    @Mock
    private MeetingHostWebSocketHandler hostWebSocketHandler;
    @Mock
    private XfyunOnlineTtsSynthesizeService ttsSynthesizeService;
    @Mock
    private MeetingService meetingService;

    private MeetingHostSessionService service;
    private MeetingRuntimeConfig runtimeConfig;

    @BeforeEach
    void setUp() {
        runtimeConfig = new MeetingRuntimeConfig();
        runtimeConfig.setEnabled(true);
        runtimeConfig.setRollCallEnabled(true);
        runtimeConfig.setAutoRollCallAfterOpening(true);
        runtimeConfig.setTtsEnabled(false);
        runtimeConfig.setAgendaEnabled(true);
        service = new MeetingHostSessionService(
                meetingMapper,
                participantMapper,
                presetMapper,
                presetAgendaDocService,
                hostWebSocketHandler,
                ttsSynthesizeService,
                new ObjectMapper(),
                runtimeConfig,
                new MeetingHostRuntimeProperties(),
                new MeetingAudioProperties(),
                meetingService);
    }

    private HostStartRequest agendaWithoutRollCall() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("事项进度通报");
        item.setMinutes(10);
        HostStartRequest body = new HostStartRequest();
        body.setItems(List.of(item));
        return body;
    }

    @Test
    @DisplayName("startRollCall：议程无检点会序时拒绝")
    void startRollCall_rejectsWithoutRollCallChapter() {
        String meetingId = "m-no-rc";
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setChatId("chat-1");
        meeting.setPresetTypeCode(1);
        meeting.setStatus(MeetingStatus.STARTED.name());
        when(meetingMapper.selectById(eq(meetingId))).thenReturn(meeting);

        service.start(meetingId, agendaWithoutRollCall());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.startRollCall(meetingId));
        assertTrue(ex.getMessage().contains("检点"));
    }

    @Test
    @DisplayName("canAutoStartRollCall：无检点会序时为 false（即使有应到名单）")
    void canAutoStartRollCall_falseWithoutAgendaChapter() {
        Meeting meeting = new Meeting();
        meeting.setId("m-2");
        meeting.setPresetTypeCode(1);

        HostStartRequest body = agendaWithoutRollCall();
        @SuppressWarnings("unchecked")
        List<Object> topics = (List<Object>) ReflectionTestUtils.invokeMethod(
                service, "resolveTopics", meeting, body);

        boolean can = (boolean) ReflectionTestUtils.invokeMethod(
                service, "canAutoStartRollCall", meeting, topics);
        assertFalse(can);
    }
}
