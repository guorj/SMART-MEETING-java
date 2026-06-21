package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingTodoDto {
    private String id;
    private String meetingId;
    private Integer presetTypeCode;
    private String content;
    private String assigneeId;
    private String assigneeName;
    private String operatorId;
    private String operatorName;
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
