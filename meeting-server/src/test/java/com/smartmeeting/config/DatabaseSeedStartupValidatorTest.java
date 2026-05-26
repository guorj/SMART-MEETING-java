package com.smartmeeting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.repository.MeetingMinuteMapper;
import com.smartmeeting.service.PresetAgendaDocService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSeedStartupValidatorTest {

    @Mock
    private MeetingMinuteMapper meetingMinuteMapper;
    @Mock
    private PresetAgendaDocService presetAgendaDocService;

    private DatabaseSeedStartupValidator validator;

    @BeforeEach
    void setUp() {
        Environment env = new MockEnvironment()
                .withProperty("spring.profiles.active", "dev")
                .withProperty("spring.sql.init.mode", "never");
        MeetingDatabaseProperties databaseProperties = new MeetingDatabaseProperties();
        validator = new DatabaseSeedStartupValidator(env, databaseProperties,
                meetingMinuteMapper, presetAgendaDocService, new ObjectMapper());
        when(meetingMinuteMapper.selectCount(any())).thenReturn(0L);
        when(presetAgendaDocService.findReportBindingForAgenda(anyInt(), anyInt()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void validateOnStartup_readsPresetHostAgendaOnly() {
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setHostAgenda("""
                {"version":2,"items":[{"title":"x"},{"title":"会序2","docs":[{"configName":"c1","role":"SOURCE","slot":0,"url":"https://ovjde0k7vc1.feishu.cn/docx/doxTest123","enabled":true}]}]}
                """);
        when(presetAgendaDocService.getPresetCached(1)).thenReturn(preset);
        validator.validateOnStartup();
        verify(presetAgendaDocService).getPresetCached(1);
        verify(presetAgendaDocService).refreshPresetBundle(1);
    }
}
