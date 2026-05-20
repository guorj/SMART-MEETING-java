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
 * 本地内存事件总线，在禁用 Kafka 时替代 {@link KafkaProducer} 与消费者链路。
 * <p>
 * 当 {@code meeting.kafka.enabled=false}（或未配置，默认 false）时生效，在单进程内异步执行：
 * 会议结束 → 纪要生成 → 待办提取，行为与 {@link MinuteGenerateConsumer} +
 * {@link TodoExtractConsumer} 串联近似，但不经过消息中间件。
 * </p>
 * <p>
 * 关键协作组件：{@link MinuteGenerationService}、{@link TodoExtractionService}；
 * 可选注册 {@link #addMinuteListener} / {@link #addTodoListener} 做扩展观测（当前发布路径未遍历监听器列表）。
 * </p>
 *
 * @see KafkaProducer
 * @see MinuteGenerateConsumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meeting.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LocalEventBus {

    private final MinuteGenerationService minuteGenerationService;
    private final TodoExtractionService todoExtractionService;

    /** 纪要生成事件的本地订阅者（线程安全列表） */
    private final CopyOnWriteArrayList<Consumer<MinuteGenerateMessage>> minuteListeners = new CopyOnWriteArrayList<>();

    /** 待办提取事件的本地订阅者（线程安全列表） */
    private final CopyOnWriteArrayList<Consumer<TodoExtractMessage>> todoListeners = new CopyOnWriteArrayList<>();

    /**
     * 发布会议结束事件，触发纪要生成并在同链路内继续待办提取。
     * <p>
     * 等价于向 Kafka {@code meeting.events} 投递 {@link MinuteGenerateMessage} 后由消费者处理；
     * 使用 {@code meetingTaskExecutor} 线程池异步执行，不阻塞调用方。
     * </p>
     *
     * @param message 含 meetingId、audioPath 等字段的纪要生成请求
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
            todoExtractionService.extractTodos(message.getMeetingId());
            
        } catch (Exception e) {
            log.error("[LocalEventBus] Failed to process meeting event: meetingId={}", 
                    message.getMeetingId(), e);
        }
    }

    /**
     * 发布待办提取事件，单独触发待办抽取（可携带已生成的纪要正文）。
     * <p>
     * 等价于向 Kafka {@code todo.extract} 投递消息；与 {@link #publishMeetingEvent} 内嵌的
     * {@link TodoExtractionService#extractTodos(String)} 不同，本方法可传入 {@code minuteText}。
     * </p>
     *
     * @param message 待办提取请求，含 meetingId，可选 minuteText
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
     * 注册纪要生成事件的本地监听器（供测试或扩展埋点使用）。
     *
     * @param listener 收到 {@link MinuteGenerateMessage} 时的回调，不可为 null
     */
    public void addMinuteListener(Consumer<MinuteGenerateMessage> listener) {
        minuteListeners.add(listener);
    }

    /**
     * 注册待办提取事件的本地监听器（供测试或扩展埋点使用）。
     *
     * @param listener 收到 {@link TodoExtractMessage} 时的回调，不可为 null
     */
    public void addTodoListener(Consumer<TodoExtractMessage> listener) {
        todoListeners.add(listener);
    }
}
