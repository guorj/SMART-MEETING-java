package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.database")
public class MeetingDatabaseProperties {

    /**
     * 启动后只读校验会序飞书配置（不写库）。生产/开发建议保持 true。
     */
    private boolean validateSeedOnStartup = true;
}
