package com.smartmeeting.admin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.BotBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/push-logs")
@RequiredArgsConstructor
public class PushLogAdminController {

    private final BotBridgeService botBridge;

    @GetMapping
    public ApiResponse<JsonNode> list(
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String meetingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(botBridge.get("/api/logs", Map.of(
                "taskId", taskId,
                "batchId", batchId,
                "meetingId", meetingId,
                "status", status,
                "triggerType", triggerType,
                "page", String.valueOf(page),
                "size", String.valueOf(size))));
    }
}
