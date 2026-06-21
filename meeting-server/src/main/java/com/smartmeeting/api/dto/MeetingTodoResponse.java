package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单条会议待办响应体。
 * <p>
 * 用于 {@code GET /api/v1/meetings/{id}/todos} 及待办更新接口的 {@code data} 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingTodoResponse {
    private String id;
    private String meetingId;
    private String content;
    private String assigneeId;
    private String assigneeName;
    private String operatorId;
    private String operatorName;
    /** {@link com.smartmeeting.enums.TodoStatus} 枚举名 */
    private String status;
    private String priority;
    private LocalDateTime deadline;
    private LocalDateTime completedAt;
    private String completionNote;
    private String blockReason;
    private LocalDateTime lastRemindAt;
    private Integer remindCount;
    private String nextMeetingId;
    private Boolean reportedInNext;
    private LocalDateTime createdAt;
}
