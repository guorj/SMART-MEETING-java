package com.smartmeeting.matterprogress.openclaw;

/**
 * OpenClaw Gateway 任务标识：用于 {@code chat.send} 的 idempotencyKey 与会话隔离。
 */
public final class OpenClawTaskIds {

    public static final String TASK_MINUTE_ENHANCEMENT = "minute_enhancement";
    public static final String TASK_MINUTE_GENERATION = "minute_generation";

    private OpenClawTaskIds() {
    }

    /** 纪要增强：{@code minute_enhancement:{meetingId}:{nonce}} */
    public static String minuteEnhancement(String meetingId, long nonce) {
        return TASK_MINUTE_ENHANCEMENT + ":" + sanitize(meetingId) + ":" + nonce;
    }

    /** 纪要 Skill 生成：{@code minute_generation:{meetingId}:{nonce}} */
    public static String minuteGeneration(String meetingId, long nonce) {
        return TASK_MINUTE_GENERATION + ":" + sanitize(meetingId) + ":" + nonce;
    }

    public static boolean isMinuteEnhancementTask(String taskId) {
        return taskId != null && taskId.startsWith(TASK_MINUTE_ENHANCEMENT + ":");
    }

    public static boolean isMinuteGenerationTask(String taskId) {
        return taskId != null && taskId.startsWith(TASK_MINUTE_GENERATION + ":");
    }

    private static String sanitize(String meetingId) {
        if (meetingId == null || meetingId.isBlank()) {
            return "unknown";
        }
        return meetingId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
