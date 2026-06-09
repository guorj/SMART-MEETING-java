package com.smartmeeting.api.config;

import com.smartmeeting.config.MeetingAsyncProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private final MeetingAsyncProperties asyncProperties;

    public AsyncConfig(MeetingAsyncProperties asyncProperties) {
        this.asyncProperties = asyncProperties;
    }

    @Bean("meetingTaskExecutor")
    public Executor meetingTaskExecutor() {
        int core = Math.max(1, asyncProperties.getTaskExecutorCore());
        int max = Math.max(core, asyncProperties.getTaskExecutorMax());
        int queueFloor = Math.max(1, asyncProperties.getTaskExecutorQueueFloor());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(Math.max(queueFloor, asyncProperties.getTaskExecutorQueue()));
        executor.setThreadNamePrefix("meeting-");
        executor.initialize();
        return executor;
    }

    @Bean("asrTaskExecutor")
    public Executor asrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("asr-");
        executor.initialize();
        return executor;
    }
}
