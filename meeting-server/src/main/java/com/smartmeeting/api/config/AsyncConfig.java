package com.smartmeeting.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置。
 * <p>
 * 为会议通用异步任务与 ASR 相关任务分别提供命名线程池。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 会议业务通用异步执行器（核心 4、最大 8 线程，队列 100）。
     *
     * @return 名为 {@code meetingTaskExecutor} 的线程池
     */
    @Bean("meetingTaskExecutor")
    public Executor meetingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("meeting-");
        executor.initialize();
        return executor;
    }

    /**
     * ASR 相关异步执行器（核心 2、最大 4 线程，队列 50）。
     *
     * @return 名为 {@code asrTaskExecutor} 的线程池
     */
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
