package com.smartmeeting.config.oabp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 渲染后 sheet 的展示元数据（主持页读此渲染 badge/分组等）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpSheetDisplayMeta {
    private String displayMode;
    private List<String> columnRenderAs = new ArrayList<>();
    private List<OabpSheetGroupMeta> groups = new ArrayList<>();
    /** 合计行在 rows 中的下标（0-based）；null 表示无合计行 */
    private Integer totalRowIndex;
    /** true：严格按 SQL 行序/plain 展示，跳过模板排序分组与主持页状态分组 */
    private Boolean sqlStrict;
}
