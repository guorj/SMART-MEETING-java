package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.async")
public class MeetingAsyncProperties {

    private int taskExecutorCore = 16;
    private int taskExecutorMax = 32;
    private int taskExecutorQueue = 200;
    /** 线程池队列容量下限（须 ≥1）。 */
    private int taskExecutorQueueFloor = 10;
}
