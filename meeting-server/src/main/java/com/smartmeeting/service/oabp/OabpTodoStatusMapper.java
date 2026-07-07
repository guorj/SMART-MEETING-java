package com.smartmeeting.service.oabp;

import com.smartmeeting.enums.TodoStatus;

/**
 * meeting {@link TodoStatus} → oabp 数字状态映射工具。
 * <p>
 * oabp {@code jq_todos_task.status} 约定：0=未开始，1=进行中，2=已完成，3=已延期。
 * </p>
 * <p>
 * 映射规则：
 * <ul>
 *   <li>{@link TodoStatus#PENDING} → 0</li>
 *   <li>{@link TodoStatus#IN_PROGRESS} / {@link TodoStatus#BLOCKED} /
 *       {@link TodoStatus#OVERDUE} / {@link TodoStatus#PENDING_DECISION} → 1（按进行中处理）</li>
 *   <li>{@link TodoStatus#COMPLETED} → 2</li>
 *   <li>{@link TodoStatus#DELAYED} → 3</li>
 * </ul>
 */
public final class OabpTodoStatusMapper {

    private OabpTodoStatusMapper() {
    }

    /**
     * 将 meeting 待办状态枚举名映射为 oabp 数字状态。
     *
     * @param meetingStatus {@link TodoStatus} 枚举名（大小写敏感）；null 或未知返回 1（进行中兜底）
     * @return oabp 数字状态 0/1/2/3
     */
    public static Integer toOabpStatus(String meetingStatus) {
        if (meetingStatus == null || meetingStatus.isBlank()) {
            return 1;
        }
        try {
            return toOabpStatus(TodoStatus.valueOf(meetingStatus.trim()));
        } catch (IllegalArgumentException e) {
            return 1;
        }
    }

    /**
     * 将 meeting 待办状态枚举映射为 oabp 数字状态。
     *
     * @param status meeting 待办状态枚举；null 返回 1（进行中兜底）
     * @return oabp 数字状态 0/1/2/3
     */
    public static Integer toOabpStatus(TodoStatus status) {
        if (status == null) {
            return 1;
        }
        return switch (status) {
            case PENDING -> 0;
            case COMPLETED -> 2;
            case DELAYED -> 3;
            default -> 1;
        };
    }
}
