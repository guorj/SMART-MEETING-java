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
    /** 决策人飞书 user_id（缓存自 OABP decision_maker_user_id；NULL 表示无决策人） */
    private String decisionMakerFeishuUserId;
    /** 决策人姓名 */
    private String decisionMakerName;
    /** 是否处于已提交待裁决态 */
    private Boolean pendingDecision;
    /** 决策人作出裁决的时间 */
    private LocalDateTime decisionMadeAt;
    /** 裁决结果：APPROVED|DELAYED|REJECTED */
    private String decisionResult;
    /** 决策人裁决备注 */
    private String decisionNote;
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
    /** 父待办 ID（拆分来源）；NULL 表示原始待办 */
    private String parentId;
    private LocalDateTime createdAt;
}
