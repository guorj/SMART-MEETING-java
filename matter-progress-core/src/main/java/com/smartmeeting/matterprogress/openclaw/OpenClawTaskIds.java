package com.smartmeeting.matterprogress.openclaw;

/**
 * OpenClaw Gateway 任务标识：用于 {@code chat.send} 的 idempotencyKey、会话隔离与写回校验。
 */
public final class OpenClawTaskIds {

    public static final String TASK_MATTER_PROGRESS = "matter_progress";
    public static final String TASK_MINUTE_ENHANCEMENT = "minute_enhancement";

    private OpenClawTaskIds() {
    }

    /** 会序通报：{@code briefing:{meetingId}:{agendaIndex}:{generation}} */
    public static String briefing(String meetingId, int agendaIndex, int generation) {
        return "briefing:" + sanitize(meetingId) + ":" + agendaIndex + ":" + Math.max(0, generation);
    }

    /** 定时事项对比通报：{@code weekly-comparison:{jobId}} */
    public static String weeklyComparison(long jobId) {
        return "weekly-comparison:" + Math.max(0L, jobId);
    }

    /** 纪要增强：{@code minute_enhancement:{meetingId}:{nonce}} */
    public static String minuteEnhancement(String meetingId, long nonce) {
        return TASK_MINUTE_ENHANCEMENT + ":" + sanitize(meetingId) + ":" + nonce;
    }

    public static boolean isBriefingTask(String taskId) {
        return taskId != null && taskId.startsWith("briefing:");
    }

    public static boolean isWeeklyComparisonTask(String taskId) {
        return taskId != null && taskId.startsWith("weekly-comparison:");
    }

    public static boolean isMinuteEnhancementTask(String taskId) {
        return taskId != null && taskId.startsWith(TASK_MINUTE_ENHANCEMENT + ":");
    }

    private static String sanitize(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return "unknown";
        }
        return meetingId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
