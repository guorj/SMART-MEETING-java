package com.smartmeeting.enums;

/**
 * 待办优先级枚举。
 * <p>
 * 持久化于 {@code int_meeting_todo.priority}。
 */
public enum Priority {
    /** 低优先级 */
    LOW,
    /** 中优先级 */
    MEDIUM,
    /** 高优先级 */
    HIGH,
    /** 紧急 */
    URGENT
}
