package com.smartmeeting.enums;

/**
 * 会议待办状态枚举。
 * <p>
 * 持久化于 {@code int_meeting_todo.status}。
 */
public enum TodoStatus {
    /** 待处理 */
    PENDING,
    /** 进行中 */
    IN_PROGRESS,
    /** 已完成 */
    COMPLETED,
    /** 被阻塞，无法推进 */
    BLOCKED,
    /** 已逾期（超过 deadline 仍未完成） */
    OVERDUE,
    /** 已延期（主动申请延后） */
    DELAYED,
    /**
     * 已提交完成待裁决（二段式裁决中间态）。
     * <p>
     * 责任人点击"提交完成"后，若 OABP {@code jq_todos_task.decision_maker_user_id}
     * 有真实决策人，则置此态并推送裁决卡给决策人；决策人裁决后转 {@link #COMPLETED} /
     * {@link #DELAYED} / {@link #IN_PROGRESS}。
     * <p>
     * 状态流转：{@code PENDING/IN_PROGRESS → PENDING_DECISION → COMPLETED/DELAYED/IN_PROGRESS}。
     */
    PENDING_DECISION
}
