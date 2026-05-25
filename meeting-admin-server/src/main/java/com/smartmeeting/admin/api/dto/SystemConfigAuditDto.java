package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SystemConfigAuditDto {
    private Long id;
    private String configKey;
    private String action;
    private String oldValueJson;
    private String newValueJson;
    private String operator;
    private LocalDateTime createdAt;
}
