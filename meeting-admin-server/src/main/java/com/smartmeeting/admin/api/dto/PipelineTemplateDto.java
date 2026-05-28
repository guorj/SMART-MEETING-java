package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class PipelineTemplateDto {
    private Long id;
    private String templateCode;
    private String templateName;
    private String stage;
    private boolean enabled;
    private Integer versionNo;
    private String description;
}
