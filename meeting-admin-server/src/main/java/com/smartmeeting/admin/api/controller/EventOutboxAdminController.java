package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.EventOutboxDto;
import com.smartmeeting.admin.service.EventOutboxAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/event-outbox")
@RequiredArgsConstructor
public class EventOutboxAdminController {

    private final EventOutboxAdminService eventOutboxAdminService;

    @GetMapping
    public ApiResponse<Page<EventOutboxDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String eventType) {
        return ApiResponse.ok(eventOutboxAdminService.list(page, size, status, aggregateType, aggregateId, eventType));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(eventOutboxAdminService.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<EventOutboxDto> get(@PathVariable long id) {
        return ApiResponse.ok(eventOutboxAdminService.get(id));
    }

    @PostMapping("/{id}/republish")
    public ApiResponse<Void> republish(@PathVariable long id) {
        eventOutboxAdminService.republish(id);
        return ApiResponse.ok();
    }

    @PostMapping("/republish-batch")
    public ApiResponse<Map<String, Integer>> republishBatch(@RequestBody(required = false) BatchReq body) {
        String status = body != null ? body.getStatus() : null;
        int count = eventOutboxAdminService.republishBatch(status);
        return ApiResponse.ok(Map.of("reset", count));
    }

    @PostMapping("/clean-published")
    public ApiResponse<Map<String, Integer>> cleanPublished(@RequestBody(required = false) CleanReq body) {
        int retainHours = body != null && body.getRetainHours() != null ? body.getRetainHours() : 24;
        int count = eventOutboxAdminService.cleanPublished(retainHours);
        return ApiResponse.ok(Map.of("deleted", count));
    }

    @Data
    public static class BatchReq {
        private String status;
    }

    @Data
    public static class CleanReq {
        private Integer retainHours;
    }
}
