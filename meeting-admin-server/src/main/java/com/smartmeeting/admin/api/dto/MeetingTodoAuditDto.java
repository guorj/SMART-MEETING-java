package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingTodoAuditDto {
    private Long id;
    private String todoId;
    private String action;
    private String oldStatus;
    private String newStatus;
    private String operatorId;
    private String operatorName;
    private String reason;
    private String payloadJson;
    private LocalDateTime createdAt;
}
