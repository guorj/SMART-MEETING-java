package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上次会议待办进度响应（F-MID-02）
 *
 * 用于会议开始时展示上次会议的待办完成情况：
 * - 总待办数、已完成数、进行中数、延期数
 * - 延期项详情列表（供会议通报）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviousProgressResponse {

    /** 上次会议ID */
    private String lastMeetingId;

    /** 上次会议标题 */
    private String lastMeetingTitle;

    /** 上次会议结束时间 */
    private LocalDateTime lastMeetingTime;

    /** 总待办数 */
    private int totalCount;

    /** 已完成数 */
    private int completedCount;

    /** 进行中数 */
    private int inProgressCount;

    /** 已延期数 */
    private int delayedCount;

    /** 延期项详情列表 */
    private List<DelayedItem> delayedItems;

    /**
     * 延期待办项详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DelayedItem {

        /** 待办内容 */
        private String content;

        /** 责任人姓名 */
        private String assigneeName;

        /** 延期原因/卡点 */
        private String blockReason;

        /** 原截止时间 */
        private LocalDateTime deadline;
    }
}