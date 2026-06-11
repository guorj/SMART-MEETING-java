package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelWorkbookDto { private List<ExcelSheetDto> sheets; }
