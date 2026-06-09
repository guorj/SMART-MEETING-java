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
    /** 功能依赖父键；空表示无父级。 */
    private String parentKey;
    /** 父键 effective 为 true 时方可编辑（布尔子项）。 */
    private boolean editable;
    /** 仅 YAML/env 可改，修改后须重启。 */
    private boolean requiresRestart;
    /** INTEGER 下限（含）；null 表示不限。 */
    private Integer minValue;
    /** INTEGER 上限（含）；null 表示不限。 */
    private Integer maxValue;
    /** 浮点/阈值下限（含）；null 表示不限。 */
    private Double doubleMinValue;
    /** 浮点/阈值上限（含）；null 表示不限。 */
    private Double doubleMaxValue;
    /** 取值范围说明，供 Admin 表单展示。 */
    private String valueRangeHint;
}
