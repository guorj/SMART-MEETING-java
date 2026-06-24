package com.smartmeeting.admin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.BotBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
        Map<String, String> query = new LinkedHashMap<>();
        if (taskId != null && !taskId.isBlank()) query.put("taskId", taskId);
        if (batchId != null && !batchId.isBlank()) query.put("batchId", batchId);
        if (meetingId != null && !meetingId.isBlank()) query.put("meetingId", meetingId);
        if (status != null && !status.isBlank()) query.put("status", status);
        if (triggerType != null && !triggerType.isBlank()) query.put("triggerType", triggerType);
        query.put("page", String.valueOf(page));
        query.put("size", String.valueOf(size));
        return ApiResponse.ok(botBridge.get("/api/logs", query));
    }
}
