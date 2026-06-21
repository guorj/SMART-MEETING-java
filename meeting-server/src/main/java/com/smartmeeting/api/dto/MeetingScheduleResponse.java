package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingScheduleResponse {
    private String meetingId;
    private LocalDateTime scheduledTime;
    private boolean calendarSynced;
    private String calendarReason;
    private String eventId;
}
