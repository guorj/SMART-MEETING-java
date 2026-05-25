package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemConfigSchemaItemDto {
    private String key;
    private String category;
    private String type;
    private String defaultValue;
    private String currentValue;
    private boolean hotReloadable;
    private String description;
    private boolean sensitive;
}
