package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingTodoSaveDto {
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
    private String completionNote;
    private String blockReason;
}
