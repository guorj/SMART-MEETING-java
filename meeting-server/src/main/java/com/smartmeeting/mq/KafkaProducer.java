package com.smartmeeting.mq;

import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.TodoExtractMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 会议相关 Kafka 消息的生产者封装。
 * <p>
 * 使用 {@link KafkaTemplate} 异步发送，以 {@code meetingId} 作为消息 key 保证同会议消息进入同一分区。
 * 仅在 {@code meeting.kafka.enabled=true} 时注入；开发环境由 {@link LocalEventBus} 替代。
 * </p>
 *
 * @see MinuteGenerateConsumer
 * @see TodoExtractConsumer
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送会议纪要生成任务到指定主题（通常为 {@code meeting.events}）。
     *
     * @param topic   Kafka 主题名
     * @param message 纪要生成载荷，须包含 {@link MinuteGenerateMessage#getMeetingId()}
     */
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

    /**
     * 发送待办提取任务到指定主题（通常为 {@code todo.extract}）。
     *
     * @param topic   Kafka 主题名
     * @param message 待办提取载荷，须包含 {@link TodoExtractMessage#getMeetingId()}
     */
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
