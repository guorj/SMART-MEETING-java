package com.smartmeeting.config.oabp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 分组展示元数据（行范围指向 flat rows）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpSheetGroupMeta {
    private String title;
    private int startRow;
    private int rowCount;
}
