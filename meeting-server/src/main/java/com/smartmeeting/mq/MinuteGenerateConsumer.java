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

/**
 * Kafka 会议纪要生成链路的入口消费者。
 * <p>
 * 订阅 {@code meeting.events} 主题，收到会议结束类事件后依次：
 * </p>
 * <ol>
 *   <li>调用 {@link MinuteGenerationService#generateMinute(String, String)} 生成纪要</li>
 *   <li>通过 {@link KafkaProducer#sendTodoExtract(String, TodoExtractMessage)} 投递待办提取任务至 {@code todo.extract}</li>
 * </ol>
 * <p>
 * 仅在 {@code meeting.kafka.enabled=true} 时注册；与 {@link TodoExtractConsumer} 组成异步流水线。
 * </p>
 *
 * @see MinuteGenerateMessage
 * @see KafkaProducer
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MinuteGenerateConsumer {

    private final MinuteGenerationService minuteGenerationService;
    private final TodoExtractionService todoExtractionService;
    private final KafkaProducer kafkaProducer;

    /**
     * 消费会议纪要生成事件并触发后续待办提取投递。
     *
     * @param record Kafka 消费记录，value 为 {@link MinuteGenerateMessage}（含 meetingId、audioPath 等）
     * @param ack    手动确认偏移量；无论成功失败当前实现均 ack，避免重复阻塞
     */
    @KafkaListener(topics = "meeting.events", groupId = "minute-generate")
    public void consume(ConsumerRecord<String, MinuteGenerateMessage> record, Acknowledgment ack) {
        MinuteGenerateMessage message = record.value();
        log.info("Consuming minute generate event: meetingId={}", message.getMeetingId());

        try {
            minuteGenerationService.generateMinute(message.getMeetingId(), message.getAudioPath());

            TodoExtractMessage todoMsg = TodoExtractMessage.builder()
                    .meetingId(message.getMeetingId())
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
