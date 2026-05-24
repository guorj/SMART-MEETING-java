package com.smartmeeting.matterprogress.feishu;

/**
 * 多维表格 base 下的一个数据表（飞书 UI 中的 sheet / 数据表页签）。
 *
 * @param tableId   tbl 开头 ID
 * @param tableName 数据表显示名
 */
public record BitableTableInfo(String tableId, String tableName) {
}
