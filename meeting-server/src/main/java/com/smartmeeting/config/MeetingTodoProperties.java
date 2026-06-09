package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议待办提取与同步配置，绑定 {@code meeting.todo.*} 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.todo")
public class MeetingTodoProperties {

    /** 会后是否执行待办提取与同步链路 */
    private boolean extractionEnabled = false;
}

