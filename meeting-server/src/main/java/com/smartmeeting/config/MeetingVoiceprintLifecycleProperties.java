package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.voiceprint.lifecycle")
public class MeetingVoiceprintLifecycleProperties {

    private int expireYears = 10;
    private int expiringWarningHours = 48;
}
