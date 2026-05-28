package com.smartmeeting.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka Topic 声明配置。
 * <p>
 * 当 {@code meeting.kafka.enabled=true} 时自动创建会议事件与待办抽取两个 Topic。
 */
@Configuration
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    /**
     * 会议生命周期事件 Topic（3 分区，副本因子 1）。
     *
     * @return {@code meeting.events} Topic 定义
     */
    @Bean
    public NewTopic meetingEventsTopic() {
        return new NewTopic("meeting.events", 3, (short) 1);
    }

    /**
     * 待办抽取任务 Topic（3 分区，副本因子 1）。
     *
     * @return {@code todo.extract} Topic 定义
     */
    @Bean
    public NewTopic todoExtractTopic() {
        return new NewTopic("todo.extract", 3, (short) 1);
    }

    @Bean
    public NewTopic meetingEventsDltTopic() {
        return new NewTopic("meeting.events.DLT", 3, (short) 1);
    }

    @Bean
    public NewTopic todoExtractDltTopic() {
        return new NewTopic("todo.extract.DLT", 3, (short) 1);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setSyncCommits(true);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
