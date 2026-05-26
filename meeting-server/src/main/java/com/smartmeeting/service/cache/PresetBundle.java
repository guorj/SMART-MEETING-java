package com.smartmeeting.service.cache;

import com.smartmeeting.entity.MeetingTypePreset;

/**
 * 会务预设 DB 快照（写入 Redis 前的载体）；资料已内嵌 {@code host_agenda} JSON。
 */
public record PresetBundle(MeetingTypePreset preset) {

    public int embeddedDocCount() {
        if (preset == null || preset.getHostAgenda() == null || preset.getHostAgenda().isBlank()) {
            return 0;
        }
        return com.smartmeeting.config.agenda.PresetAgendaMergeEngine.extractBindingsFromHostAgenda(
                preset.getCode() != null ? preset.getCode() : 0,
                preset.getHostAgenda(),
                new com.fasterxml.jackson.databind.ObjectMapper()).size();
    }
}
