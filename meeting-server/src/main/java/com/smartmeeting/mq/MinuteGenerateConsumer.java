package com.smartmeeting.mq;

import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.TodoExtractMessage;
import com.smartmeeting.service.MinuteGenerationService;
import com.smartmeeting.service.TodoExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MinuteGenerateConsumer {

    private final MinuteGenerationService minuteGenerationService;
    private final TodoExtractionService todoExtractionService;
    private final KafkaProducer kafkaProducer;

    @KafkaListener(topics = "meeting.events", groupId = "minute-generate")
    public void consume(ConsumerRecord<String, MinuteGenerateMessage> record, Acknowledgment ack) {
        MinuteGenerateMessage message = record.value();
        log.info("Consuming minute generate event: meetingId={}", message.getMeetingId());

        try {
            minuteGenerationService.generateMinute(message.getMeetingId(), message.getAudioPath());

            TodoExtractMessage todoMsg = TodoExtractMessage.builder()
                    .meetingId(message.getMeetingId())
                    .minuteText("")
                    .sentAt(System.currentTimeMillis())
                    .build();
            kafkaProducer.sendTodoExtract("todo.extract", todoMsg);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to generate minute: meetingId={}", message.getMeetingId(), e);
            ack.acknowledge();
        }
    }
}
