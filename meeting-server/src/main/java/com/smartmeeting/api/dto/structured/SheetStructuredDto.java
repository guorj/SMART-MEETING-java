package com.smartmeeting.api.dto.structured;

import com.smartmeeting.config.oabp.OabpSheetDisplayMeta;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 电子表格结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SheetStructuredDto {
    private String sheetName;
    private List<String> headers;
    private List<List<String>> rows;
    private List<MergedRangeDto> mergedRanges;
    private List<Integer> columnWidths;
    private int headerRowCount;
    /** 展示模板渲染元数据；无模板时为 null（主持页走旧逻辑） */
    private OabpSheetDisplayMeta displayMeta;
}
