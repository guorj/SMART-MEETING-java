package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 会话/缓存 TTL，绑定 {@code meeting.session.*}（须重启）。 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.session")
public class MeetingSessionProperties {

    private int feishuStartPendingTtlMinutes = 30;
    private int feishuUserLastGroupChatTtlHours = 48;
}
