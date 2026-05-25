package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemConfigEntryDto {
    private String key;
    private String category;
    private String valueJson;
}
