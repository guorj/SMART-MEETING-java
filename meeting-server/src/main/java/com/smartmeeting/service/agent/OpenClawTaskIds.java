package com.smartmeeting.service.agent;

/**
 * meeting-server 对 OpenClaw 任务标识的薄委托层。
 *
 * <p>核心逻辑已迁至 {@link com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds}。
 */
public final class OpenClawTaskIds {

    public static final String TASK_MATTER_PROGRESS =
            com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.TASK_MATTER_PROGRESS;
    public static final String TASK_MINUTE_ENHANCEMENT =
            com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.TASK_MINUTE_ENHANCEMENT;

    private OpenClawTaskIds() {
    }

    public static String briefing(String meetingId, int agendaIndex, int generation) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.briefing(meetingId, agendaIndex, generation);
    }

    public static String minuteEnhancement(String meetingId, long nonce) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.minuteEnhancement(meetingId, nonce);
    }

    public static boolean isBriefingTask(String taskId) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.isBriefingTask(taskId);
    }

    public static boolean isMinuteEnhancementTask(String taskId) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds.isMinuteEnhancementTask(taskId);
    }
}
