package com.smartmeeting.api.dto;

import lombok.Data;

@Data
public class ParticipantAdminUpdateRequest {
    private String userId;
    private String name;
    private String attendanceMode;
}
