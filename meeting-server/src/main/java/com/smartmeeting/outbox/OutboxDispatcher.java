package com.smartmeeting.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.OutboxEvent;
import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.OfflineAsrMessage;
import com.smartmeeting.model.TodoExtractMessage;
import com.smartmeeting.mq.KafkaProducer;
import com.smartmeeting.mq.LocalEventBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<KafkaProducer> kafkaProducerProvider;
    private final ObjectProvider<LocalEventBus> localEventBusProvider;

    public void dispatch(OutboxEvent event) throws Exception {
        if (OutboxEventTypes.MINUTE_GENERATE.equals(event.getEventType())) {
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
            return;
        }
        if (OutboxEventTypes.OFFLINE_ASR.equals(event.getEventType())) {
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
            return;
        }
        if (OutboxEventTypes.TODO_EXTRACT.equals(event.getEventType())) {
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
    }
}
