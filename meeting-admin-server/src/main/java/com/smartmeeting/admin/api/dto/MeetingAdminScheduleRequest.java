package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingAdminScheduleRequest {
    private LocalDateTime scheduledTime;
    private Integer durationMinutes;
    private Boolean syncCalendar;
    private Boolean notifyChat;
}
