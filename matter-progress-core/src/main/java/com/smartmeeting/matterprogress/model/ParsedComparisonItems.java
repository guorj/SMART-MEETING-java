package com.smartmeeting.matterprogress.model;

import java.util.List;

/**
 * Agent / Legacy 产出解析结果。
 * <ul>
 *   <li>{@code success=false}：JSON/Markdown 整体解析失败 → run FAILED</li>
 *   <li>{@code success=true, discardedCount>0}：部分 item 字段缺失或 statusLabel/category 不一致 → run PARTIAL</li>
 *   <li>{@code success=true, discardedCount=0}：正常 → run READY（含 0 条 item 情况）</li>
 * </ul>
 * <p>{@code runId}：Agent 写库模式下（§5），Agent 自行 INSERT run+item 后回传的 run_id；
 * 为 {@code null} 或 {@code <=0} 表示 Agent 未写库（旧模式 / 写库失败），Bot 走自身入库流程。
 */
public record ParsedComparisonItems(
        boolean success,
        List<WeeklyComparisonItem> items,
        int discardedCount,
        String errorMessage,
        Long runId
) {
    public static ParsedComparisonItems ok(List<WeeklyComparisonItem> items, int discardedCount) {
        return new ParsedComparisonItems(true, items, discardedCount, null, null);
    }

    public static ParsedComparisonItems okWithRunId(List<WeeklyComparisonItem> items, int discardedCount, Long runId) {
        return new ParsedComparisonItems(true, items, discardedCount, null, runId);
    }

    public static ParsedComparisonItems failed(String error) {
        return new ParsedComparisonItems(false, List.of(), 0, error, null);
    }

    public String resolveRunStatus() {
        if (!success) {
            return WeeklyComparisonRun.FAILED;
        }
        return discardedCount > 0 ? WeeklyComparisonRun.PARTIAL : WeeklyComparisonRun.READY;
    }

    /** Agent 是否自行写库（run+item 已由 Agent INSERT，Bot 应跳过自身入库、走校验回写分支）。 */
    public boolean agentWroteRun() {
        return success && runId != null && runId > 0;
    }
}
