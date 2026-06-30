package com.smartmeeting.matterprogress.model;

/** 会前事项对比通报单条事项（对应 int_weekly_matter_comparison_item 一行） */
public record WeeklyComparisonItem(
        String category,
        String matterName,
        String assignee,
        String timeNode,
        String statusLabel,
        int sortOrder,
        String sourceConfigName
) {
    /** category 枚举 */
    public static final String DELAYED = "DELAYED";
    public static final String COMPLETED = "COMPLETED";
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /** statusLabel ↔ category 一致性校验 */
    public static String categoryOfStatusLabel(String statusLabel) {
        if (statusLabel == null) {
            return null;
        }
        String s = statusLabel.trim();
        return switch (s) {
            case "延期" -> DELAYED;
            case "已完成" -> COMPLETED;
            case "进行中" -> IN_PROGRESS;
            default -> null;
        };
    }

    public boolean isCategoryConsistent() {
        String expected = categoryOfStatusLabel(statusLabel);
        return expected != null && expected.equals(category);
    }
}
