package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.config.RuntimeBridgeProperties;
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
        links.put("reverseProxyHint", "生产可用 Nginx 将 /admin、/api/v1/admin 反代到 :8766，/api/v1/meetings 到 :8765");
        return ApiResponse.ok(links);
    }
}
