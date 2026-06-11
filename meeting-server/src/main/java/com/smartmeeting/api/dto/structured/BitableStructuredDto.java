package com.smartmeeting.api.dto.structured;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多维表格结构化输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitableStructuredDto {
    private String tableName;
    private List<BitableColumnDto> columns;
    private List<BitableRecordDto> records;
    private int totalRecords;
    private List<BitableGroupDto> groups;
    /** 无 table= 时 base 下全部数据表 */
    private List<BitableStructuredDto> tables;
}
