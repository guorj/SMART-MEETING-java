package com.smartmeeting.config;

import com.smartmeeting.entity.MeetingSystemConfig;
import com.smartmeeting.repository.MeetingSystemConfigMapper;
import com.smartmeeting.service.DashboardGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingRuntimeConfigLoaderTest {

    @Mock
    private MeetingSystemConfigMapper configMapper;
    @Mock
    private DashboardGrantService dashboardGrantService;

    private MeetingRuntimeConfigLoader loader;
    private MeetingMinuteProperties minuteProperties;

    @BeforeEach
    void setUp() {
        minuteProperties = new MeetingMinuteProperties();
        loader = new MeetingRuntimeConfigLoader(
                configMapper,
                new MeetingRuntimeConfig(),
                minuteProperties,
                new MeetingAsrProperties(),
                new MeetingTodoProperties(),
                new MeetingPipelineProperties(),
                new MeetingSchedulerProperties(),
                new MeetingNotificationProperties(),
                new MeetingVoiceprintProperties(),
                new MeetingIsvProperties(),
                new MeetingWebProperties(),
                new MeetingVcProperties(),
                new OpenClawProperties(),
                new ObjectMapper(),
                dashboardGrantService);
        when(configMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void reload_appliesDbOverrideThenResetRestoresFactoryDefaults() {
        loader.reload();
        assertFalse(minuteProperties.isGenerationEnabled());

        MeetingSystemConfig row = new MeetingSystemConfig();
        row.setConfigKey("meeting.minute.generation-enabled");
        row.setValueJson("true");
        when(configMapper.selectList(any())).thenReturn(List.of(row));

        loader.reload();
        assertTrue(minuteProperties.isGenerationEnabled());

        when(configMapper.selectList(any())).thenReturn(List.of());
        loader.reload();
        assertFalse(minuteProperties.isGenerationEnabled());
    }

    @Test
    void reload_appliesStringAndBooleanRowsFromDb() {
        MeetingSystemConfig boolRow = new MeetingSystemConfig();
        boolRow.setConfigKey("meeting.minute.generation-enabled");
        boolRow.setValueJson("true");

        MeetingSystemConfig strRow = new MeetingSystemConfig();
        strRow.setConfigKey("meeting.host.reminder.meeting-minutes-left");
        strRow.setValueJson("\"10,3\"");

        MeetingSystemConfig modeRow = new MeetingSystemConfig();
        modeRow.setConfigKey("meeting.asr.offline-role-mode");
        modeRow.setValueJson("\"auto\"");

        when(configMapper.selectList(any())).thenReturn(List.of(boolRow, strRow, modeRow));

        MeetingRuntimeConfig host = new MeetingRuntimeConfig();
        MeetingAsrProperties asr = new MeetingAsrProperties();
        loader = new MeetingRuntimeConfigLoader(
                configMapper, host, minuteProperties, asr,
                new MeetingTodoProperties(), new MeetingPipelineProperties(),
                new MeetingSchedulerProperties(), new MeetingNotificationProperties(),
                new MeetingVoiceprintProperties(), new MeetingIsvProperties(),
                new MeetingWebProperties(), new MeetingVcProperties(),
                new OpenClawProperties(),
                new ObjectMapper(), dashboardGrantService);

        loader.reload();

        assertTrue(minuteProperties.isGenerationEnabled());
        assertEquals("10,3", host.getReminder().getMeetingMinutesLeft());
        assertEquals("auto", asr.getOfflineRoleMode());
    }

    @Test
    void reload_cascadesHostChildrenOffWhenMasterDisabled() {
        MeetingSystemConfig masterOff = new MeetingSystemConfig();
        masterOff.setConfigKey("meeting.host.enabled");
        masterOff.setValueJson("false");

        MeetingSystemConfig ttsOn = new MeetingSystemConfig();
        ttsOn.setConfigKey("meeting.host.tts-enabled");
        ttsOn.setValueJson("true");

        MeetingSystemConfig rollCallOn = new MeetingSystemConfig();
        rollCallOn.setConfigKey("meeting.host.roll-call-enabled");
        rollCallOn.setValueJson("true");

        MeetingSystemConfig autoRollCallOn = new MeetingSystemConfig();
        autoRollCallOn.setConfigKey("meeting.host.auto-roll-call-after-opening");
        autoRollCallOn.setValueJson("true");

        when(configMapper.selectList(any())).thenReturn(List.of(masterOff, ttsOn, rollCallOn, autoRollCallOn));

        MeetingRuntimeConfig host = new MeetingRuntimeConfig();
        loader = new MeetingRuntimeConfigLoader(
                configMapper, host, minuteProperties, new MeetingAsrProperties(),
                new MeetingTodoProperties(), new MeetingPipelineProperties(),
                new MeetingSchedulerProperties(), new MeetingNotificationProperties(),
                new MeetingVoiceprintProperties(), new MeetingIsvProperties(),
                new MeetingWebProperties(), new MeetingVcProperties(),
                new OpenClawProperties(),
                new ObjectMapper(), dashboardGrantService);

        loader.reload();

        assertFalse(host.isEnabled());
        assertFalse(host.isTtsEnabled());
        assertFalse(host.isRollCallEnabled());
        assertFalse(host.isAutoRollCallAfterOpening());
    }
}
