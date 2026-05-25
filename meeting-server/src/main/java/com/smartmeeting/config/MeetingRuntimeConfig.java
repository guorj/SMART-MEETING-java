package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会中运行时配置：YAML 默认值 + DB 覆盖（{@link MeetingRuntimeConfigLoader}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.host")
public class MeetingRuntimeConfig {
    private boolean enabled = true;
    private boolean agendaEnabled = true;
    private boolean ttsEnabled = true;
    private boolean rollCallEnabled = true;
    private boolean autoRollCallAfterOpening = true;
    private RollCall rollCall = new RollCall();

    @Data
    public static class RollCall {
        private int windowSeconds = 12;
        private int asrGraceSeconds = 6;
        private int onlineInventorySeconds = 60;
    }
}
