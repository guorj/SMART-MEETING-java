package com.smartmeeting.api.dto.structured;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多维表格单条记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitableRecordDto {
    private String recordId;
    /** fieldName -> displayValue */
    private Map<String, Object> fields;
}
