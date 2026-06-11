package com.smartmeeting.api.dto.structured;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多维表格分组信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitableGroupDto {
    private String field;
    private String value;
    private List<BitableRecordDto> records;
}
