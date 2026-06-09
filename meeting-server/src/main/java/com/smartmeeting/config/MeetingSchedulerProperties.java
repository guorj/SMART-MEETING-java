package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.scheduler")
public class MeetingSchedulerProperties {

    private boolean preEnabled = false;
    private int preWindowMinutes = 5;
    private String pre24hTemplateCode = "";
    private String pre10mTemplateCode = "";
    private long scanMs = 300000L;
}
