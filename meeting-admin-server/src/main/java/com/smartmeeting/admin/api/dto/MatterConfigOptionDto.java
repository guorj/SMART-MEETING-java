package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatterConfigOptionDto {
    private String configName;
    private String configRole;
    private Integer presetTypeCode;
    private Integer enabled;
}
