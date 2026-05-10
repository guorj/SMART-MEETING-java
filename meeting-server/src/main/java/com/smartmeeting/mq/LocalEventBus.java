package com.smartmeeting.mq;

import com.smartmeeting.model.MinuteGenerateMessage;
import com.smartmeeting.model.TodoExtractMessage;
import com.smartmeeting.service.MinuteGenerationService;
import com.smartmeeting.service.TodoExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 本地内存事件总线 - 开发/测试环境替代 Kafka
 * 当 meeting.kafka.enabled=false 时使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEventBus {

    private final MinuteGenerationService minuteGenerationService;
    private final TodoExtractionService todoExtractionService;

    // 事件监听器
    private final CopyOnWriteArrayList<Consumer<MinuteGenerateMessage>> minuteListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<TodoExtractMessage>> todoListeners = new CopyOnWriteArrayList<>();

    /**
     * 发送会议结束事件 → 触发纪要生成链
     * 替代 Kafka meeting.events Topic
     */
    @Async("meetingTaskExecutor")
    public void publishMeetingEvent(MinuteGenerateMessage message) {
        log.info("[LocalEventBus] Publishing meeting event: meetingId={}", message.getMeetingId());
        
        // 模拟 Kafka 消费者处理
        try {
            // 执行纪要生成链路
            minuteGenerationService.generateMinute(message.getMeetingId(), message.getAudioPath());
            
            log.info("[LocalEventBus] Minute generation completed: meetingId={}", message.getMeetingId());
            
            // 触发待办提取（替代 todo.extract Topic）
            TodoExtractMessage todoMsg = TodoExtractMessage.builder()
                    .meetingId(message.getMeetingId())
                    .minuteText("")  // TODO: 从纪要服务获取实际内容
                    .sentAt(System.currentTimeMillis())
                    .build();
            publishTodoExtract(todoMsg);
            
        } catch (Exception e) {
            log.error("[LocalEventBus] Failed to process meeting event: meetingId={}", 
                    message.getMeetingId(), e);
        }
    }

    /**
     * 发送待办提取事件
     * 替代 Kafka todo.extract Topic
     */
    @Async("meetingTaskExecutor")
    public void publishTodoExtract(TodoExtractMessage message) {
        log.info("[LocalEventBus] Publishing todo extract event: meetingId={}", message.getMeetingId());
        
        try {
            todoExtractionService.extractTodos(message.getMeetingId(), message.getMinuteText());
            log.info("[LocalEventBus] Todo extraction completed: meetingId={}", message.getMeetingId());
        } catch (Exception e) {
            log.error("[LocalEventBus] Failed to process todo extract: meetingId={}", 
                    message.getMeetingId(), e);
        }
    }

    /**
     * 注册纪要生成监听器
     */
    public void addMinuteListener(Consumer<MinuteGenerateMessage> listener) {
        minuteListeners.add(listener);
    }

    /**
     * 注册待办提取监听器
     */
    public void addTodoListener(Consumer<TodoExtractMessage> listener) {
        todoListeners.add(listener);
    }
}
