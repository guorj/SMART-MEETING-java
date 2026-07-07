package com.smartmeeting.outbox;

import com.smartmeeting.event.OabpFollowupSyncRequestedEvent;
import com.smartmeeting.event.OabpSubtaskSyncRequestedEvent;
import com.smartmeeting.event.OabpTaskWritebackRequestedEvent;
import com.smartmeeting.model.OabpFollowupSyncMessage;
import com.smartmeeting.model.OabpSubtaskSyncMessage;
import com.smartmeeting.model.OabpTaskWritebackMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * oabp 跨库领域事件 → outbox 写入监听器。
 *
 * <p>监听 {@link OabpTaskWritebackRequestedEvent}、{@link OabpFollowupSyncRequestedEvent}
 * 与 {@link OabpSubtaskSyncRequestedEvent}，在主库事务 {@code AFTER_COMMIT} 阶段写入
 * {@code int_event_outbox}，由 {@link OutboxPoller} 异步消费。</p>
 *
 * <p><b>启用条件</b>：仅在 {@code meeting.datasource.external.oabp.enabled=true} 时注册。
 * 未启用时领域事件被忽略（无监听器），业务方发布事件无需判空。</p>
 *
 * <p><b>幂等键</b>：{@code eventKey} 包含 meetingTodoId + sentAt，同一待办同一时刻的事件只写一次 outbox；
 * outbox 表 {@code uk_event_outbox_key} 唯一索引兜底，重复插入被静默捕获（见 {@link OutboxWriter}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpDomainEventOutboxListener {

    private final OutboxWriter outboxWriter;

    /**
     * 处理任务回写请求：写入 outbox，事件类型 {@link OutboxEventTypes#OABP_TASK_WRITEBACK}。
     *
     * @param event 任务回写请求事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOabpTaskWritebackRequested(OabpTaskWritebackRequestedEvent event) {
        OabpTaskWritebackMessage payload = event.payload();
        if (payload == null) {
            log.warn("OabpTaskWritebackRequestedEvent payload is null, meetingTodoId={}", event.meetingTodoId());
            return;
        }
        outboxWriter.write(
                "MEETING_TODO",
                event.meetingTodoId(),
                OutboxEventTypes.OABP_TASK_WRITEBACK,
                "oabp-task-writeback:" + event.meetingTodoId() + ":" + event.sentAt(),
                payload);
        log.debug("oabp task writeback outbox written: meetingTodoId={}", event.meetingTodoId());
    }

    /**
     * 处理跟进同步请求：写入 outbox，事件类型 {@link OutboxEventTypes#OABP_FOLLOWUP_SYNC}。
     *
     * @param event 跟进同步请求事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOabpFollowupSyncRequested(OabpFollowupSyncRequestedEvent event) {
        OabpFollowupSyncMessage payload = event.payload();
        if (payload == null) {
            log.warn("OabpFollowupSyncRequestedEvent payload is null, meetingTodoId={}", event.meetingTodoId());
            return;
        }
        outboxWriter.write(
                "MEETING_TODO",
                event.meetingTodoId(),
                OutboxEventTypes.OABP_FOLLOWUP_SYNC,
                "oabp-followup-sync:" + event.meetingTodoId() + ":" + event.sentAt(),
                payload);
        log.debug("oabp followup sync outbox written: meetingTodoId={}", event.meetingTodoId());
    }

    /**
     * 处理子任务（执行人）同步请求：写入 outbox，事件类型 {@link OutboxEventTypes#OABP_SUBTASK_SYNC}。
     *
     * @param event 子任务同步请求事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOabpSubtaskSyncRequested(OabpSubtaskSyncRequestedEvent event) {
        OabpSubtaskSyncMessage payload = event.payload();
        if (payload == null) {
            log.warn("OabpSubtaskSyncRequestedEvent payload is null, meetingTodoId={}", event.meetingTodoId());
            return;
        }
        outboxWriter.write(
                "MEETING_TODO",
                event.meetingTodoId(),
                OutboxEventTypes.OABP_SUBTASK_SYNC,
                "oabp-subtask-sync:" + event.meetingTodoId() + ":" + event.sentAt(),
                payload);
        log.debug("oabp subtask sync outbox written: meetingTodoId={}", event.meetingTodoId());
    }
}
