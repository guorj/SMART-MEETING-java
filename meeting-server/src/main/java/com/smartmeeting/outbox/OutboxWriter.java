package com.smartmeeting.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.OutboxEvent;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    public void write(String aggregateType, String aggregateId, String eventType, String eventKey, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setEventKey(eventKey);
        event.setPayloadJson(toJson(payload));
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        try {
            outboxEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("Outbox write skipped: type={}, key={}, reason={}", eventType, eventKey, e.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化 outbox 事件失败: " + e.getMessage());
        }
    }
}
