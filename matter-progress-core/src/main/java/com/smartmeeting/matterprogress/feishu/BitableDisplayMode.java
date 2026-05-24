package com.smartmeeting.matterprogress.feishu;

import java.util.Locale;

/**
 * 多维表格会中 plainText 导出模式。
 * <ul>
 *   <li>{@link #GROUPED} — 近三个月 + 已完成/延期/进行中归纳</li>
 *   <li>{@link #RAW} — 按 API 顺序平铺，无时间/状态分区</li>
 * </ul>
 */
public enum BitableDisplayMode {

    RAW,
    GROUPED;

    public static BitableDisplayMode from(String value) {
        if (value == null || value.isBlank()) {
            return GROUPED;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "RAW" -> RAW;
            case "GROUPED" -> GROUPED;
            default -> GROUPED;
        };
    }
}
