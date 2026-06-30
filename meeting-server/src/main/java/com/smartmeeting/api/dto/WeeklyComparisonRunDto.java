package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 会前事项对比通报运行批次头（int_weekly_matter_comparison_run 一行） */
@Data
@Builder
public class WeeklyComparisonRunDto {
    private Long id;
    private String title;
    private int itemCount;
    /** READY|FAILED|PARTIAL */
    private String generationStatus;
    private LocalDateTime generatedAt;
    private String runError;
}
