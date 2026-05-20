package com.smartmeeting.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
