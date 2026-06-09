package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Admin/API 分页与安全上限，绑定 {@code meeting.api.*}（须重启）。 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.api")
public class MeetingApiProperties {

    private int dashboardListLimitMax = 30;
    private int systemConfigAuditLimitMax = 100;
    private int observabilityLimitMax = 500;
    private int pipelineTodoRemindLimitMax = 500;
}
