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

/**
 * Kafka 待办提取消息消费者。
 * <p>
 * 订阅 {@code todo.extract} 主题，在纪要生成完成后由 {@link MinuteGenerateConsumer} 或
 * {@link KafkaProducer#sendTodoExtract(String, TodoExtractMessage)} 投递消息，委托
 * {@link TodoExtractionService} 从会议内容中抽取待办事项。
 * </p>
 * <p>
 * 仅在配置 {@code meeting.kafka.enabled=true} 时启用；本地开发可改用 {@link LocalEventBus}。
 * </p>
 *
 * @see TodoExtractMessage
 * @see TodoExtractionService
 * @see MinuteGenerateConsumer
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TodoExtractConsumer {

    private final TodoExtractionService todoExtractionService;

    /**
     * 消费单条待办提取事件并执行业务逻辑。
     * <p>
     * 处理成功或失败后均手动确认（ack），避免 poison message 阻塞分区；失败场景仅记录日志。
     * </p>
     *
     * @param record Kafka 消费记录，value 为 {@link TodoExtractMessage}
     * @param ack    手动提交偏移量用的确认句柄
     */
    @KafkaListener(topics = "todo.extract", groupId = "todo-extract")
    public void consume(ConsumerRecord<String, TodoExtractMessage> record, Acknowledgment ack) {
        TodoExtractMessage message = record.value();
        log.info("Consuming todo extract event: meetingId={}", message.getMeetingId());

        try {
            todoExtractionService.extractTodos(message.getMeetingId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to extract todos: meetingId={}", message.getMeetingId(), e);
            throw new IllegalStateException("todo extract failed: " + message.getMeetingId(), e);
        }
    }
}
