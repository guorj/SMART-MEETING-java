package com.smartmeeting.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.OutboxEvent;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.OabpFollowupSyncMessage;
import com.smartmeeting.model.OabpSubtaskSyncMessage;
import com.smartmeeting.model.OabpTaskWritebackMessage;
import com.smartmeeting.model.OfflineAsrMessage;
import com.smartmeeting.model.TodoExtractMessage;
import com.smartmeeting.mq.KafkaProducer;
import com.smartmeeting.mq.LocalEventBus;
import com.smartmeeting.service.oabp.outbox.OabpFollowupSyncConsumer;
import com.smartmeeting.service.oabp.outbox.OabpSubtaskSyncConsumer;
import com.smartmeeting.service.oabp.outbox.OabpTaskWritebackConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件分发器：按 {@link OutboxEvent#getEventType()} 路由到对应消费者。
 *
 * <p>分发失败时抛出异常，由 {@link OutboxPoller} 捕获并更新 outbox 状态为 RETRY/FAILED。</p>
 *
 * <p><b>oabp 事件</b>：消费者用 {@code ObjectProvider} 延迟获取，因为
 * {@code meeting.datasource.external.oabp.enabled=false} 时不注册 Bean；
 * 此时若收到 oabp 事件，记录警告并抛异常触发重试（启用 oabp 后即恢复）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<KafkaProducer> kafkaProducerProvider;
    private final ObjectProvider<LocalEventBus> localEventBusProvider;
    private final ObjectProvider<OabpTaskWritebackConsumer> oabpTaskWritebackConsumerProvider;
    private final ObjectProvider<OabpFollowupSyncConsumer> oabpFollowupSyncConsumerProvider;
    private final ObjectProvider<OabpSubtaskSyncConsumer> oabpSubtaskSyncConsumerProvider;

    /**
     * 分发单个 outbox 事件到对应消费者。
     *
     * @param event outbox 事件
     * @throws Exception 消费失败时抛出，由 {@link OutboxPoller} 重试
     */
    public void dispatch(OutboxEvent event) throws Exception {
        String type = event.getEventType();
        if (OutboxEventTypes.MINUTE_GENERATE.equals(type)) {
            dispatchMinuteGenerate(event);
            return;
        }
        if (OutboxEventTypes.OFFLINE_ASR.equals(type)) {
            dispatchOfflineAsr(event);
            return;
        }
        if (OutboxEventTypes.TODO_EXTRACT.equals(type)) {
            dispatchTodoExtract(event);
            return;
        }
        if (OutboxEventTypes.OABP_TASK_WRITEBACK.equals(type)) {
            dispatchOabpTaskWriteback(event);
            return;
        }
        if (OutboxEventTypes.OABP_FOLLOWUP_SYNC.equals(type)) {
            dispatchOabpFollowupSync(event);
            return;
        }
        if (OutboxEventTypes.OABP_SUBTASK_SYNC.equals(type)) {
            dispatchOabpSubtaskSync(event);
            return;
        }
        log.warn("Unknown outbox event type: {}, id={}", type, event.getId());
    }

    private void dispatchMinuteGenerate(OutboxEvent event) throws Exception {
        MinuteGenerateMessage payload = objectMapper.readValue(event.getPayloadJson(), MinuteGenerateMessage.class);
        KafkaProducer kafkaProducer = kafkaProducerProvider.getIfAvailable();
        LocalEventBus localEventBus = localEventBusProvider.getIfAvailable();
        try {
            if (kafkaProducer != null) {
                kafkaProducer.sendMinuteGenerate("meeting.events", payload);
            } else if (localEventBus != null) {
                localEventBus.publishMeetingEvent(payload);
            }
        } catch (Exception ex) {
            log.warn("Kafka minute dispatch failed, fallback local bus: {}", ex.getMessage());
            if (localEventBus != null) {
                localEventBus.publishMeetingEvent(payload);
            }
        }
    }

    private void dispatchOfflineAsr(OutboxEvent event) throws Exception {
        OfflineAsrMessage payload = objectMapper.readValue(event.getPayloadJson(), OfflineAsrMessage.class);
        KafkaProducer kafkaProducer = kafkaProducerProvider.getIfAvailable();
        LocalEventBus localEventBus = localEventBusProvider.getIfAvailable();
        try {
            if (kafkaProducer != null) {
                kafkaProducer.sendOfflineAsr("meeting.offline.asr", payload);
            } else if (localEventBus != null) {
                localEventBus.publishOfflineAsr(payload);
            }
        } catch (Exception ex) {
            log.warn("Kafka offline ASR dispatch failed, fallback local bus: {}", ex.getMessage());
            if (localEventBus != null) {
                localEventBus.publishOfflineAsr(payload);
            }
        }
    }

    private void dispatchTodoExtract(OutboxEvent event) throws Exception {
        TodoExtractMessage payload = objectMapper.readValue(event.getPayloadJson(), TodoExtractMessage.class);
        KafkaProducer kafkaProducer = kafkaProducerProvider.getIfAvailable();
        LocalEventBus localEventBus = localEventBusProvider.getIfAvailable();
        try {
            if (kafkaProducer != null) {
                kafkaProducer.sendTodoExtract("todo.extract", payload);
            } else if (localEventBus != null) {
                localEventBus.publishTodoExtract(payload);
            }
        } catch (Exception ex) {
            log.warn("Kafka todo dispatch failed, fallback local bus: {}", ex.getMessage());
            if (localEventBus != null) {
                localEventBus.publishTodoExtract(payload);
            }
        }
    }

    /**
     * 分发 oabp 任务回写事件。
     *
     * @param event outbox 事件
     * @throws Exception 消费者未启用或执行失败时抛出
     */
    private void dispatchOabpTaskWriteback(OutboxEvent event) throws Exception {
        OabpTaskWritebackConsumer consumer = oabpTaskWritebackConsumerProvider.getIfAvailable();
        if (consumer == null) {
            throw new IllegalStateException(
                    "OabpTaskWritebackConsumer 未启用（meeting.datasource.external.oabp.enabled=false），"
                            + "无法处理事件 id=" + event.getId());
        }
        OabpTaskWritebackMessage payload = objectMapper.readValue(
                event.getPayloadJson(), OabpTaskWritebackMessage.class);
        consumer.consume(payload);
    }

    /**
     * 分发 oabp 跟进同步事件。
     *
     * @param event outbox 事件
     * @throws Exception 消费者未启用或执行失败时抛出
     */
    private void dispatchOabpFollowupSync(OutboxEvent event) throws Exception {
        OabpFollowupSyncConsumer consumer = oabpFollowupSyncConsumerProvider.getIfAvailable();
        if (consumer == null) {
            throw new IllegalStateException(
                    "OabpFollowupSyncConsumer 未启用（meeting.datasource.external.oabp.enabled=false），"
                            + "无法处理事件 id=" + event.getId());
        }
        OabpFollowupSyncMessage payload = objectMapper.readValue(
                event.getPayloadJson(), OabpFollowupSyncMessage.class);
        consumer.consume(payload);
    }

    /**
     * 分发 oabp 子任务（执行人）同步事件。
     *
     * @param event outbox 事件
     * @throws Exception 消费者未启用或执行失败时抛出
     */
    private void dispatchOabpSubtaskSync(OutboxEvent event) throws Exception {
        OabpSubtaskSyncConsumer consumer = oabpSubtaskSyncConsumerProvider.getIfAvailable();
        if (consumer == null) {
            throw new IllegalStateException(
                    "OabpSubtaskSyncConsumer 未启用（meeting.datasource.external.oabp.enabled=false），"
                            + "无法处理事件 id=" + event.getId());
        }
        OabpSubtaskSyncMessage payload = objectMapper.readValue(
                event.getPayloadJson(), OabpSubtaskSyncMessage.class);
        consumer.consume(payload);
    }
}
