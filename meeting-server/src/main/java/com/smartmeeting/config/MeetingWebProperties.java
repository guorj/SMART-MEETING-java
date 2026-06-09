package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.web")
public class MeetingWebProperties {

    private int staticCacheSeconds = 300;
    private String pageCacheBuster = "";
}
