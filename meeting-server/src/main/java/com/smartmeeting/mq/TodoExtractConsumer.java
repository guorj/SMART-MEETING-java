package com.smartmeeting.mq;

import com.smartmeeting.model.TodoExtractMessage;
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
public class TodoExtractConsumer {

    private final TodoExtractionService todoExtractionService;

    @KafkaListener(topics = "todo.extract", groupId = "todo-extract")
    public void consume(ConsumerRecord<String, TodoExtractMessage> record, Acknowledgment ack) {
        TodoExtractMessage message = record.value();
        log.info("Consuming todo extract event: meetingId={}", message.getMeetingId());

        try {
            todoExtractionService.extractTodos(message.getMeetingId(), message.getMinuteText());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to extract todos: meetingId={}", message.getMeetingId(), e);
            ack.acknowledge();
        }
    }
}
