package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingTodoResponse {
    private String id;
    private String meetingId;
    private String content;
    private String assigneeId;
    private String assigneeName;
    private String status;
    private String priority;
    private LocalDateTime deadline;
    private LocalDateTime completedAt;
    private String completionNote;
    private String blockReason;
    private LocalDateTime lastRemindAt;
    private Integer remindCount;
    private String nextMeetingId;
    private Boolean reportedInNext;
    private LocalDateTime createdAt;
}
