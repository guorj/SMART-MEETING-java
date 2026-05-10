package com.smartmeeting.mq;

import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.TodoExtractMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendMinuteGenerate(String topic, MinuteGenerateMessage message) {
        kafkaTemplate.send(topic, message.getMeetingId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send minute generate message: meetingId={}",
                                message.getMeetingId(), ex);
                    } else {
                        log.info("Sent minute generate message: meetingId={}, partition={}, offset={}",
                                message.getMeetingId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void sendTodoExtract(String topic, TodoExtractMessage message) {
        kafkaTemplate.send(topic, message.getMeetingId(), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send todo extract message: meetingId={}",
                                message.getMeetingId(), ex);
                    } else {
                        log.info("Sent todo extract message: meetingId={}, partition={}, offset={}",
                                message.getMeetingId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
