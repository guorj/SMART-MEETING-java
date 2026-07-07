package com.smartmeeting.event;

import com.smartmeeting.model.OabpFollowupSyncMessage;

/**
 * oabp 跟进同步请求领域事件。
 *
 * <p>业务方在主库事务内发布此事件（经 {@link DomainEventPublisher}），
 * 由 {@code OabpFollowupSyncOutboxListener} 在 {@code AFTER_COMMIT} 阶段写入 outbox，
 * 最终由 {@link com.smartmeeting.service.oabp.outbox.OabpFollowupSyncConsumer} 异步消费写 oabp。</p>
 *
 * @param meetingTodoId meeting 待办 ID
 * @param payload       跟进同步消息体
 * @param sentAt        发送时刻（epoch 毫秒）
 */
public record OabpFollowupSyncRequestedEvent(
        String meetingTodoId,
        OabpFollowupSyncMessage payload,
        long sentAt
) {
}
