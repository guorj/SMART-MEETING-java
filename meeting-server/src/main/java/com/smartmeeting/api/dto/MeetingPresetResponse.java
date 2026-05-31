package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会务类型预设响应体（{@code GET /api/v1/meeting-type-presets} 列表项）。
 * <p>
 * 描述固定预设的展示名、组织信息、议程摘要及 AI 主持模板 JSON。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingPresetResponse {
    /** 预设编号（来源于 int_meeting_type_preset.code） */
    private Integer code;
    private String displayName;
    private String company;
    private String department;
    private String groupName;
    private String scheduleNote;
    private String agendaSummary;
    private String organizerName;
    private String leaderName;
    private List<String> participantNames;
    /** AI 主持议题模板 JSON（与 int_meeting_type_preset.host_agenda 一致） */
    private String hostAgenda;
}
