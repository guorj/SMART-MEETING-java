package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.config.RuntimeBridgeProperties;
import com.smartmeeting.admin.service.BotBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class IntegrationsAdminController {

    private final RuntimeBridgeProperties bridgeProperties;
    private final BotBridgeService botBridge;

    @GetMapping("/links")
    public ApiResponse<Map<String, String>> links() {
        Map<String, String> links = new LinkedHashMap<>();
        String meeting = bridgeProperties.getMeetingServerBaseUrl().replaceAll("/$", "");
        String bot = bridgeProperties.getBotBaseUrl().replaceAll("/$", "");
        links.put("meetingServer", meeting);
        links.put("meetingHostExample", meeting + "/host/{meetingId}");
        links.put("feishuBot", bot);
        links.put("feishuBotExecute", bot + "/execute");
        links.put("feishuBotDocs", "feishu-scheduled-bot/docs/USER-MANUAL.md");
        links.put("reverseProxyHint", "生产：OA /api/ → :8081；meeting-server /meeting-server/ → :8765（保留 URI 前缀）");
        return ApiResponse.ok(links);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("bot", botBridge.healthCheck());
        Map<String, Object> meeting = new LinkedHashMap<>();
        meeting.put("baseUrl", bridgeProperties.getMeetingServerBaseUrl());
        meeting.put("configured", bridgeProperties.getInternalReloadToken() != null
                && !bridgeProperties.getInternalReloadToken().isBlank());
        health.put("meetingServer", meeting);
        return ApiResponse.ok(health);
    }
}
