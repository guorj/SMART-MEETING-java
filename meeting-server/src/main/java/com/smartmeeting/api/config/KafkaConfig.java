package com.smartmeeting.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public NewTopic meetingEventsTopic() {
        return new NewTopic("meeting.events", 3, (short) 1);
    }

    @Bean
    public NewTopic todoExtractTopic() {
        return new NewTopic("todo.extract", 3, (short) 1);
    }
}
