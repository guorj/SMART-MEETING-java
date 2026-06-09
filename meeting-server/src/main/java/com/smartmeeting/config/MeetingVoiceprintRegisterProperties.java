package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 声纹注册页参数，绑定 {@code meeting.voiceprint.register.*}（须重启生效）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.voiceprint.register")
public class MeetingVoiceprintRegisterProperties {

    private int minDurationSec = 35;
    private int maxDurationSec = 90;
    private int gatewaySafeBytes = 900 * 1024;
}
