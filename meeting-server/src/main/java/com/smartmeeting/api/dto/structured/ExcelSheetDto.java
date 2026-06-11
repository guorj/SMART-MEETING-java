package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelSheetDto {
    private String sheetName; private int sheetIndex; private List<String> headers;
    private List<List<String>> rows; private List<MergedRangeDto> mergedRanges;
    private List<Integer> columnWidths; private int headerRowCount;
}
