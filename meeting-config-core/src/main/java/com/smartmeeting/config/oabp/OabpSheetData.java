package com.smartmeeting.config.oabp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 模板引擎输入/输出的表格数据（与 meeting-server SheetStructuredDto 对齐）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OabpSheetData {
    private String sheetName;
    @Builder.Default
    private List<String> headers = new ArrayList<>();
    @Builder.Default
    private List<List<String>> rows = new ArrayList<>();
    @Builder.Default
    private List<Integer> columnWidths = new ArrayList<>();
    private int headerRowCount;
    private OabpSheetDisplayMeta displayMeta;
}
