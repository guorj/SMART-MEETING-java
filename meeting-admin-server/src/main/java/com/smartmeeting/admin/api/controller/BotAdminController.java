package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.BotBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/bot")
@RequiredArgsConstructor
public class BotAdminController {

    private final BotBridgeService botBridge;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(botBridge.healthCheck());
    }

    @PostMapping("/reload-schedule")
    public ApiResponse<Map<String, Object>> reloadSchedule() {
        return ApiResponse.ok(botBridge.reloadSchedule());
    }
}
