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
}
