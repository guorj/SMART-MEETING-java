package com.smartmeeting.config.oabp;

import lombok.Builder;
import lombok.Value;

/** 内置 oabp SQL 数据源预设（简洁模式一键选用）。 */
@Value
@Builder
public class OabpSqlPreset {
    String id;
    String name;
    String description;
    String sql;
}
