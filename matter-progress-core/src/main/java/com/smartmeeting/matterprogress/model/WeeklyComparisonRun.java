package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/** 一次 job 执行的 run 批次头（对应 int_weekly_matter_comparison_run 一行） */
public record WeeklyComparisonRun(
        Long id,
        Long jobId,
        String outputConfigName,
        Integer presetTypeCode,
        Integer agendaIndex,
        String title,
        int itemCount,
        String generationStatus,
        Instant generatedAt,
        String runError
) {
    public static final String READY = "READY";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
}
