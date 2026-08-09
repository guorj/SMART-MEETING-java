package com.smartmeeting.config.oabp;

import lombok.Data;

/** 展示内容与筛选配置。 */
@Data
public class OabpDisplayContent {
    private OabpDisplayFilterNode filter;
    private Boolean includeEmptyRows;
    private Integer maxRows;
}
