package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

/** 会前事项对比通报单条事项（int_weekly_matter_comparison_item 一行） */
@Data
@Builder
public class WeeklyComparisonItemDto {
    private Long id;
    /** DELAYED|COMPLETED|IN_PROGRESS */
    private String category;
    private String matterName;
    /** 可能为 null（前端显示「未提及」） */
    private String assignee;
    private String timeNode;
    /** 延期|已完成|进行中 */
    private String statusLabel;
    private int sortOrder;
    private String sourceConfigName;
}
