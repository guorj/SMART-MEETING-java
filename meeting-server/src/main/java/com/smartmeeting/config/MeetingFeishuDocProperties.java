package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.feishu.doc")
public class MeetingFeishuDocProperties {

    private int blockBatchSize = 50;
    private int blockBatchSleepMs = 400;
}
