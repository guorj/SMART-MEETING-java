package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class WeeklyJobDto {
    private long id;
    private String jobName;
    private boolean enabled;
    private String cronExpression;
    private String scheduleTimezone;
    private List<String> sourceConfigNames;
    private String minuteQueryType;
    private String minuteQueryParamsJson;
    private String outputConfigName;
    private String outputDocTitleTpl;
    private String feishuFolderToken;
    private Instant lastRunAt;
    private String lastRunStatus;
    private String lastRunError;
}
