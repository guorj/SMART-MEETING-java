package com.smartmeeting.admin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.BotBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/push-tasks")
@RequiredArgsConstructor
public class PushTaskAdminController {

    private final BotBridgeService botBridge;

    @GetMapping
    public ApiResponse<JsonNode> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(botBridge.get("/api/tasks", Map.of("page", String.valueOf(page), "size", String.valueOf(size))));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<JsonNode> get(@PathVariable String taskId) {
        return ApiResponse.ok(botBridge.get("/api/tasks/" + taskId, null));
    }

    @PostMapping
    public ApiResponse<JsonNode> create(@RequestBody JsonNode body) {
        return ApiResponse.ok(botBridge.post("/api/tasks", body));
    }

    @PutMapping("/{taskId}")
    public ApiResponse<JsonNode> update(@PathVariable String taskId, @RequestBody JsonNode body) {
        return ApiResponse.ok(botBridge.put("/api/tasks/" + taskId, body));
    }

    @DeleteMapping("/{taskId}")
    public ApiResponse<Void> delete(@PathVariable String taskId) {
        botBridge.delete("/api/tasks/" + taskId);
        return ApiResponse.ok();
    }

    @RequestMapping(value = "/{taskId}/toggle", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ApiResponse<JsonNode> toggle(@PathVariable String taskId) {
        return ApiResponse.ok(botBridge.post("/api/tasks/" + taskId + "/toggle", null));
    }

    @PostMapping("/{taskId}/execute")
    public ApiResponse<JsonNode> execute(@PathVariable String taskId) {
        return ApiResponse.ok(botBridge.postAllowingStatuses(
                "/api/tasks/" + taskId + "/execute", null, 409, 502));
    }

    @PostMapping("/cron-preview")
    public ApiResponse<JsonNode> cronPreview(@RequestBody JsonNode body) {
        return ApiResponse.ok(botBridge.post("/api/cron/preview", body));
    }
}
