package com.smartmeeting.service;

import com.smartmeeting.config.agenda.PresetScheduleConfigCodec;
import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.repository.MeetingTypePresetMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 从会务类型预设 {@code schedule_config} 解析 instant-start 的 {@code scheduled_time}。
 */
@Service
@RequiredArgsConstructor
public class PresetScheduleTimeResolver {

    private final MeetingTypePresetMapper presetMapper;
    private final ObjectMapper objectMapper;

    public Optional<LocalDateTime> resolve(int presetTypeCode, LocalDateTime referenceTime) {
        if (presetTypeCode <= 0) {
            return Optional.empty();
        }
        MeetingTypePreset preset = presetMapper.selectById(presetTypeCode);
        if (preset == null) {
            return Optional.empty();
        }
        LocalDateTime ref = referenceTime != null ? referenceTime : LocalDateTime.now();
        LocalDateTime resolved = PresetScheduleConfigCodec.resolve(
                objectMapper, preset.getScheduleConfig(), ref);
        return Optional.of(resolved);
    }
}
