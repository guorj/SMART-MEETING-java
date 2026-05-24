package com.smartmeeting.matterprogress.model;

/** 单次 job 执行结果 */
public record WeeklyComparisonResult(
        long jobId,
        String status,
        String reportUrl,
        String errorMessage
) {
    public static WeeklyComparisonResult success(long jobId, String reportUrl) {
        return new WeeklyComparisonResult(jobId, "SUCCESS", reportUrl, null);
    }

    public static WeeklyComparisonResult failed(long jobId, String error) {
        return new WeeklyComparisonResult(jobId, "FAILED", null, error);
    }
}
