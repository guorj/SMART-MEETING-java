package com.smartmeeting.service.agent;

/**
 * meeting-server 对 OpenClaw 任务标识的薄委托层。
 *
 * <p>核心逻辑已迁至 {@link com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds}。
 */
public final class OpenClawTaskIds {

    public static final String TASK_MINUTE_ENHANCEMENT =
            com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.TASK_MINUTE_ENHANCEMENT;
    public static final String TASK_MINUTE_GENERATION =
            com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.TASK_MINUTE_GENERATION;

    private OpenClawTaskIds() {
    }

    public static String minuteEnhancement(String meetingId, long nonce) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.minuteEnhancement(meetingId, nonce);
    }

    public static String minuteGeneration(String meetingId, long nonce) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.minuteGeneration(meetingId, nonce);
    }

    public static boolean isMinuteEnhancementTask(String taskId) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.isMinuteEnhancementTask(taskId);
    }

    public static boolean isMinuteGenerationTask(String taskId) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.isMinuteGenerationTask(taskId);
    }
}
