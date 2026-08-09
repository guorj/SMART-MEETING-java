package com.smartmeeting.config.oabp;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 展示模板底部合计行。 */
@Data
public class OabpDisplayTotal {
    private String label;
    /** 要求和的源字段名列表（如 progress） */
    private List<String> sum = new ArrayList<>();
}
