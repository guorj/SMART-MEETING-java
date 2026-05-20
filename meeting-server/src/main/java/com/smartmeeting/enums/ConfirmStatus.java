package com.smartmeeting.enums;

/**
 * 参会人确认状态枚举。
 * <p>
 * 持久化于 {@code int_meeting_participant.status}，表示参会邀请的应答结果。
 */
public enum ConfirmStatus {
    /** 待确认（尚未应答邀请） */
    PENDING,
    /** 已确认参加 */
    CONFIRMED,
    /** 已拒绝参加 */
    DECLINED
}
