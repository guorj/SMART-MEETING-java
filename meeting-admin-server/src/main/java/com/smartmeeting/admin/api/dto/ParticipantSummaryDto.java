package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ParticipantSummaryDto {
    private String id;
    private String name;
    private String status;
    private String attendanceMode;
    private LocalDateTime checkedInAt;
    private String checkInSource;
}
