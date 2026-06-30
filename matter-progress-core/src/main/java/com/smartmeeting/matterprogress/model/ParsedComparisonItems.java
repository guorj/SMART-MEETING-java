package com.smartmeeting.matterprogress.model;

import java.util.List;

/**
 * Agent / Legacy 产出解析结果。
 * <ul>
 *   <li>{@code success=false}：JSON/Markdown 整体解析失败 → run FAILED</li>
 *   <li>{@code success=true, discardedCount>0}：部分 item 字段缺失或 statusLabel/category 不一致 → run PARTIAL</li>
 *   <li>{@code success=true, discardedCount=0}：正常 → run READY（含 0 条 item 情况）</li>
 * </ul>
 */
public record ParsedComparisonItems(
        boolean success,
        List<WeeklyComparisonItem> items,
        int discardedCount,
        String errorMessage
) {
    public static ParsedComparisonItems ok(List<WeeklyComparisonItem> items, int discardedCount) {
        return new ParsedComparisonItems(true, items, discardedCount, null);
    }

    public static ParsedComparisonItems failed(String error) {
        return new ParsedComparisonItems(false, List.of(), 0, error);
    }

    public String resolveRunStatus() {
        if (!success) {
            return WeeklyComparisonRun.FAILED;
        }
        return discardedCount > 0 ? WeeklyComparisonRun.PARTIAL : WeeklyComparisonRun.READY;
    }
}
