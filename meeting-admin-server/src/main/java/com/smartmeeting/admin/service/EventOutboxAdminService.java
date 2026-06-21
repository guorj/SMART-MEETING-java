package com.smartmeeting.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.EventOutboxDto;
import com.smartmeeting.admin.entity.EventOutbox;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.repository.EventOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventOutboxAdminService {

    private final EventOutboxMapper outboxMapper;

    public Page<EventOutboxDto> list(int page, int size, String status, String aggregateType,
                                     String aggregateId, String eventType) {
        Page<EventOutbox> p = new Page<>(page, size);
        LambdaQueryWrapper<EventOutbox> w = new LambdaQueryWrapper<EventOutbox>()
                .eq(status != null && !status.isBlank(), EventOutbox::getStatus, status)
                .eq(aggregateType != null && !aggregateType.isBlank(), EventOutbox::getAggregateType, aggregateType)
                .eq(aggregateId != null && !aggregateId.isBlank(), EventOutbox::getAggregateId, aggregateId)
                .eq(eventType != null && !eventType.isBlank(), EventOutbox::getEventType, eventType)
                .orderByDesc(EventOutbox::getId);
        Page<EventOutbox> result = outboxMapper.selectPage(p, w);
        Page<EventOutboxDto> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(result.getRecords().stream().map(this::toDto).toList());
        return out;
    }

    public EventOutboxDto get(long id) {
        EventOutbox row = outboxMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("事件不存在: " + id);
        }
        return toDto(row);
    }

    @Transactional
    public void republish(long id) {
        EventOutbox row = outboxMapper.selectById(id);
        if (row == null) {
            throw new BusinessException("事件不存在: " + id);
        }
        row.setStatus("PENDING");
        row.setRetryCount(0);
        row.setNextRetryAt(LocalDateTime.now());
        row.setErrorMessage(null);
        outboxMapper.updateById(row);
    }

    @Transactional
    public int republishBatch(String status) {
        String target = (status == null || status.isBlank()) ? "FAILED" : status.trim().toUpperCase();
        EventOutbox template = new EventOutbox();
        template.setStatus("PENDING");
        template.setRetryCount(0);
        template.setNextRetryAt(LocalDateTime.now());
        template.setErrorMessage(null);
        return outboxMapper.update(template, new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getStatus, target));
    }

    @Transactional
    public int cleanPublished(int retainHours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(Math.max(retainHours, 0));
        return outboxMapper.delete(new LambdaQueryWrapper<EventOutbox>()
                .eq(EventOutbox::getStatus, "PUBLISHED")
                .lt(EventOutbox::getPublishedAt, threshold));
    }

    public Map<String, Object> stats() {
        List<EventOutbox> all = outboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>()
                .select(EventOutbox::getStatus, EventOutbox::getId));
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        byStatus.put("PENDING", 0);
        byStatus.put("RETRY", 0);
        byStatus.put("PUBLISHED", 0);
        byStatus.put("FAILED", 0);
        for (EventOutbox e : all) {
            String s = e.getStatus() == null ? "PENDING" : e.getStatus();
            byStatus.merge(s, 1, Integer::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("byStatus", byStatus);
        out.put("pendingOrRetry", (byStatus.getOrDefault("PENDING", 0)) + byStatus.getOrDefault("RETRY", 0));
        out.put("failed", byStatus.getOrDefault("FAILED", 0));
        return out;
    }

    private EventOutboxDto toDto(EventOutbox row) {
        EventOutboxDto dto = new EventOutboxDto();
        dto.setId(row.getId());
        dto.setAggregateType(row.getAggregateType());
        dto.setAggregateId(row.getAggregateId());
        dto.setEventType(row.getEventType());
        dto.setEventKey(row.getEventKey());
        dto.setPayloadJson(row.getPayloadJson());
        dto.setStatus(row.getStatus());
        dto.setRetryCount(row.getRetryCount());
        dto.setNextRetryAt(row.getNextRetryAt());
        dto.setErrorMessage(row.getErrorMessage());
        dto.setPublishedAt(row.getPublishedAt());
        dto.setCreatedAt(row.getCreatedAt());
        dto.setUpdatedAt(row.getUpdatedAt());
        return dto;
    }
}
