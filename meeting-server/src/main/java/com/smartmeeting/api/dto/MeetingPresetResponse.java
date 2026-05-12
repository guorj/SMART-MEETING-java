package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingPresetResponse {
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
