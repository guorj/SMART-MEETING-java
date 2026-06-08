package com.smartmeeting.mq;

import com.smartmeeting.model.OfflineAsrMessage;
import com.smartmeeting.service.OfflineAsrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 离线 ASR 链路入口消费者，订阅 {@code meeting.offline.asr}。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OfflineAsrConsumer {

    private final OfflineAsrService offlineAsrService;

    @KafkaListener(topics = "meeting.offline.asr", groupId = "offline-asr")
    public void consume(ConsumerRecord<String, OfflineAsrMessage> record, Acknowledgment ack) {
        OfflineAsrMessage message = record.value();
        log.info("Consuming offline ASR event: meetingId={}", message.getMeetingId());
        try {
            offlineAsrService.process(message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process offline ASR: meetingId={}", message.getMeetingId(), e);
            throw new IllegalStateException("offline ASR failed: " + message.getMeetingId(), e);
        }
    }
}
