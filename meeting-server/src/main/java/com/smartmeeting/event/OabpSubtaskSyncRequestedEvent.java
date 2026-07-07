package com.smartmeeting.event;

import com.smartmeeting.model.OabpSubtaskSyncMessage;

/**
 * oabp 子任务（执行人）同步请求领域事件。
 *
 * <p>业务方在主库事务内发布此事件（经 {@link DomainEventPublisher}），
 * 由 {@code OabpDomainEventOutboxListener} 在 {@code AFTER_COMMIT} 阶段写入 outbox，
 * 最终由 {@link com.smartmeeting.service.oabp.outbox.OabpSubtaskSyncConsumer} 异步消费写 oabp
 * {@code jq_todos_subtask}。</p>
 *
 * @param meetingTodoId meeting 待办 ID
 * @param payload       子任务同步消息体（已组装好的完整载荷）
 * @param sentAt        发送时刻（epoch 毫秒）
 */
public record OabpSubtaskSyncRequestedEvent(
        String meetingTodoId,
        OabpSubtaskSyncMessage payload,
        long sentAt
) {
}
