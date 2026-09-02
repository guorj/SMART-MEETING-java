package com.smartmeeting.service.agent;

import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.service.PresetAgendaDocService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinuteSkillRouterTest {

    @Mock
    private PresetAgendaDocService presetAgendaDocService;

    @InjectMocks
    private MinuteSkillRouter router;

    @Test
    @DisplayName("preset=2 返回绑定的 skill 名")
    void preset2_returnsSkillName() {
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(2);
        preset.setMinuteSkillName("tech-committee-minutes");
        when(presetAgendaDocService.getPresetCached(2)).thenReturn(preset);

        assertThat(router.resolve(2)).isEqualTo("tech-committee-minutes");
    }

    @Test
    @DisplayName("未绑定 skill 时返回 null")
    void unbound_returnsNull() {
        MeetingTypePreset preset = new MeetingTypePreset();
        preset.setCode(1);
        preset.setMinuteSkillName(null);
        when(presetAgendaDocService.getPresetCached(1)).thenReturn(preset);

        assertThat(router.resolve(1)).isNull();
    }

    @Test
    @DisplayName("无效 presetTypeCode 返回 null")
    void invalidCode_returnsNull() {
        assertThat(router.resolve(null)).isNull();
        assertThat(router.resolve(0)).isNull();
    }
}
