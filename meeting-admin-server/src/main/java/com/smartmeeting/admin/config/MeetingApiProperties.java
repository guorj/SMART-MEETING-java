package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.api")
public class MeetingApiProperties {

    private int dashboardListLimitMax = 30;
    private int systemConfigAuditLimitMax = 100;
    private int observabilityLimitMax = 500;
    private int pipelineTodoRemindLimitMax = 500;
}
