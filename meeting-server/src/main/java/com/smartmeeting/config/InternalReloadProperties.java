package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.internal")
public class InternalReloadProperties {
    private boolean reloadEnabled = true;
    private String reloadToken = "";
}
