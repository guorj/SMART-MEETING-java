package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多维表格列定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BitableColumnDto {
    private String name;
    /** text / number / date / progress / person / url / select / checkbox / ... */
    private String type;
    private String description;
}
