package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PipelineExecutionDto {
    private Long id;
    private String meetingId;
    private Long templateId;
    private Long stepId;
    private String stage;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime timeoutAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String lastError;
}
