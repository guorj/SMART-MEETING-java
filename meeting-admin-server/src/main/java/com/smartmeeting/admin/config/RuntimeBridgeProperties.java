package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.runtime")
public class RuntimeBridgeProperties {
    private String meetingServerBaseUrl = "http://127.0.0.1:8765/meeting-server";
    private String botBaseUrl = "http://127.0.0.1:8764";
    /** feishu-scheduled-bot REST 鉴权（{@code meeting.runtime.scheduled-bot-apikey}） */
    private String scheduledBotApikey = "";
    private String internalReloadToken = "";
}
