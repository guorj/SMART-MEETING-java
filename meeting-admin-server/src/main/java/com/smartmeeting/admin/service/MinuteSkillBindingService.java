package com.smartmeeting.admin.service;

import com.smartmeeting.admin.api.dto.MinuteSkillBindingDto;
import com.smartmeeting.admin.api.dto.UpdateMinuteSkillBindingRequest;
import com.smartmeeting.admin.entity.MeetingTypePreset;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.MeetingTypePresetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会务类型与纪要 Skill 绑定维护（{@code int_meeting_type_preset.minute_skill_name}）。
 */
@Service
@RequiredArgsConstructor
public class MinuteSkillBindingService {

    private final MeetingTypePresetMapper presetMapper;
    private final MinuteSkillCatalogService catalogService;
    private final MeetingServerBridgeService meetingServerBridge;

    /**
     * 列出全部会务类型及其 Skill 绑定。
     *
     * @return 绑定列表
     */
    public List<MinuteSkillBindingDto> listBindings() {
        return presetMapper.selectList(null).stream()
                .sorted(Comparator.comparing(MeetingTypePreset::getCode))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 更新单个会务类型的 Skill 绑定并刷新 meeting-server preset 缓存。
     *
     * @param presetTypeCode 会务类型 code
     * @param request          绑定请求；{@code minuteSkillName} 为空表示解除绑定
     */
    public MinuteSkillBindingDto updateBinding(int presetTypeCode, UpdateMinuteSkillBindingRequest request) {
        MeetingTypePreset preset = presetMapper.selectById(presetTypeCode);
        if (preset == null) {
            throw new BusinessException("preset not found: " + presetTypeCode);
        }
        String skillName = normalizeSkillName(request != null ? request.getMinuteSkillName() : null);
        if (skillName != null && !catalogService.isKnownSkill(skillName)) {
            throw new BusinessException("unknown skill: " + skillName);
        }
        preset.setMinuteSkillName(skillName);
        presetMapper.updateById(preset);
        meetingServerBridge.refreshPresetCache(presetTypeCode);
        return toDto(preset);
    }

    private MinuteSkillBindingDto toDto(MeetingTypePreset preset) {
        return new MinuteSkillBindingDto(
                preset.getCode(),
                preset.getDisplayName(),
                preset.getMinuteSkillName());
    }

    private static String normalizeSkillName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
