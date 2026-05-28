package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class PipelineStepDto {
    private Long id;
    private Long templateId;
    private String stepCode;
    private String stepName;
    private String stepType;
    private String stage;
    private Integer orderNo;
    private Integer timeoutSeconds;
    private String configJson;
    private boolean enabled;
}
