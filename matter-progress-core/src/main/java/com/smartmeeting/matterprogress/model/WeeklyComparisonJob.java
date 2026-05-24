package com.smartmeeting.matterprogress.model;

import java.time.Instant;
import java.util.List;

/** int_weekly_matter_comparison_job 行映射 */
public record WeeklyComparisonJob(
        long id,
        String jobName,
        boolean enabled,
        String cronExpression,
        String scheduleTimezone,
        List<String> sourceConfigNames,
        String minuteQueryType,
        String minuteQueryParamsJson,
        String outputConfigName,
        String outputDocTitleTpl,
        String feishuFolderToken
) {
}
