package com.smartmeeting.matterprogress.model;

/** 单次 job 执行结果 */
public record WeeklyComparisonResult(
        long jobId,
        String status,
        Long runId,
        int itemCount,
        String errorMessage
) {
    public static WeeklyComparisonResult success(long jobId, Long runId, int itemCount) {
        return new WeeklyComparisonResult(jobId, "SUCCESS", runId, itemCount, null);
    }

    public static WeeklyComparisonResult partial(long jobId, Long runId, int itemCount) {
        return new WeeklyComparisonResult(jobId, "PARTIAL", runId, itemCount, null);
    }

    public static WeeklyComparisonResult failed(long jobId, String error) {
        return new WeeklyComparisonResult(jobId, "FAILED", null, 0, error);
    }
}
