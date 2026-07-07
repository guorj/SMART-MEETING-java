package com.smartmeeting.event;

import com.smartmeeting.model.OabpTaskWritebackMessage;

/**
 * oabp 任务回写请求领域事件。
 *
 * <p>业务方在主库事务内发布此事件（经 {@link DomainEventPublisher}），
 * 由 {@code OabpTaskWritebackOutboxListener} 在 {@code AFTER_COMMIT} 阶段写入 outbox，
 * 最终由 {@link com.smartmeeting.service.oabp.outbox.OabpTaskWritebackConsumer} 异步消费写 oabp。</p>
 *
 * @param meetingTodoId meeting 待办 ID
 * @param payload       回写消息体（已组装好的完整载荷）
 * @param sentAt        发送时刻（epoch 毫秒）
 */
public record OabpTaskWritebackRequestedEvent(
        String meetingTodoId,
        OabpTaskWritebackMessage payload,
        long sentAt
) {
}
