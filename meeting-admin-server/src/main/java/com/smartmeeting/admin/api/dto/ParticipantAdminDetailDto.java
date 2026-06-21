package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParticipantAdminDetailDto {
    private String id;
    private String meetingId;
    private Integer presetTypeCode;
    private String userId;
    private String name;
    private String status;
    private String attendanceMode;
    private String featureId;
    private Boolean voiceprintReady;
    private LocalDateTime checkedInAt;
    private String checkInSource;
    private Integer todoCount;
    private Integer completedCount;
}
