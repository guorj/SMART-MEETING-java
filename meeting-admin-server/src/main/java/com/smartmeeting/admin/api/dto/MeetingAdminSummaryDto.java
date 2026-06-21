package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingAdminSummaryDto {
    private String id;
    private String title;
    private String status;
    private Integer presetTypeCode;
    private String company;
    private String creatorId;
    private String chatId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
