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
    DELAYED
}
