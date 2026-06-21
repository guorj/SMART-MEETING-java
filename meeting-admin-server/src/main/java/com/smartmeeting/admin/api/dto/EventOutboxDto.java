package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventOutboxDto {
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventKey;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String errorMessage;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
