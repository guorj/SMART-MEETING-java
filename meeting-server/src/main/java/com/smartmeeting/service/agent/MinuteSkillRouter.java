package com.smartmeeting.service.agent;

import com.smartmeeting.entity.MeetingTypePreset;
import com.smartmeeting.service.PresetAgendaDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 按会务类型（{@code presetTypeCode}）解析纪要 Skill 模板名。
 *
 * <p>绑定关系存 {@code int_meeting_type_preset.minute_skill_name}，由 Admin「纪要 Skill」页维护；
 * Step 4 将该 Skill 的 SKILL.md 正文注入 LLM system prompt（不走 OpenClaw）。
 */
@Component
@RequiredArgsConstructor
public class MinuteSkillRouter {

    private final PresetAgendaDocService presetAgendaDocService;

    /**
     * 解析会务类型绑定的 Skill 名。
     *
     * @param presetTypeCode 会务类型编码；null 或非正整数返回 null
     * @return Skill 名（不含 {@code /skill:} 前缀）；未绑定或 preset 不存在时返回 null
     */
    public String resolve(Integer presetTypeCode) {
        if (presetTypeCode == null || presetTypeCode <= 0) {
            return null;
        }
        MeetingTypePreset preset = presetAgendaDocService.getPresetCached(presetTypeCode);
        if (preset == null) {
            return null;
        }
        String skillName = preset.getMinuteSkillName();
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        return skillName.trim();
    }
}
