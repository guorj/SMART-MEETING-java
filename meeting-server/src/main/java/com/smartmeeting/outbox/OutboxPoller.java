package com.smartmeeting.outbox;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.OutboxEvent;
import com.smartmeeting.repository.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxDispatcher outboxDispatcher;

    @Scheduled(fixedDelayString = "${meeting.outbox.poll-delay-ms:3000}")
    @Transactional
    public void poll() {
        try {
            LambdaQueryWrapper<OutboxEvent> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(OutboxEvent::getStatus, List.of("PENDING", "RETRY"))
                    .le(OutboxEvent::getNextRetryAt, LocalDateTime.now())
                    .orderByAsc(OutboxEvent::getId)
                    .last("LIMIT 50");
            List<OutboxEvent> pending = outboxEventMapper.selectList(wrapper);
            for (OutboxEvent event : pending) {
                handleSingle(event);
            }
        } catch (Exception e) {
            log.debug("Outbox poll skipped: {}", e.getMessage());
        }
    }

    private void handleSingle(OutboxEvent event) {
        try {
            outboxDispatcher.dispatch(event);
            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            event.setErrorMessage(null);
            outboxEventMapper.updateById(event);
        } catch (Exception e) {
            int retry = event.getRetryCount() == null ? 0 : event.getRetryCount();
            event.setRetryCount(retry + 1);
            event.setStatus(retry + 1 >= 8 ? "FAILED" : "RETRY");
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, (long) Math.pow(2, retry + 1))));
            event.setErrorMessage(shorten(e.getMessage()));
            outboxEventMapper.updateById(event);
            log.warn("Outbox dispatch failed: id={}, type={}, retry={}, err={}",
                    event.getId(), event.getEventType(), event.getRetryCount(), event.getErrorMessage());
        }
    }

    private String shorten(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }
}
