package com.smartmeeting.service.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.api.config.MeetingHostWebSocketHandler;
import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.config.MeetingHostRuntimeProperties;
import com.smartmeeting.config.MeetingRuntimeConfig;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.PresetAgendaDocService;
import com.smartmeeting.tts.XfyunOnlineTtsSynthesizeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 主持 start 幂等：会话已存在时不抛错，getStateJson 仍返回 active。
 */
@ExtendWith(MockitoExtension.class)
class MeetingHostSessionStartIdempotentTest {

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

    private MeetingHostSessionService service;
    private MeetingRuntimeConfig runtimeConfig;

    @BeforeEach
    void setUp() {
        runtimeConfig = new MeetingRuntimeConfig();
        runtimeConfig.setEnabled(true);
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
                new MeetingAudioProperties());
    }

    private HostStartRequest sampleAgenda() {
        HostAgendaItemDto item = new HostAgendaItemDto();
        item.setTitle("议题一");
        item.setMinutes(10);
        HostStartRequest body = new HostStartRequest();
        body.setItems(List.of(item));
        return body;
    }

    @Test
    @DisplayName("start 二次调用不抛错且 state 仍为 active")
    void start_isIdempotentWhenSessionAlreadyActive() {
        String meetingId = "m-idempotent";
        Meeting meeting = new Meeting();
        meeting.setId(meetingId);
        meeting.setChatId("chat-1");
        meeting.setPresetTypeCode(1);
        when(meetingMapper.selectById(eq(meetingId))).thenReturn(meeting);

        HostStartRequest body = sampleAgenda();
        service.start(meetingId, body);
        service.start(meetingId, body);

        JsonNode state = service.getStateJson(meetingId);
        assertTrue(state.get("active").asBoolean());
        assertEquals(0, state.get("currentTopicIndex").asInt());
    }
}
