package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.admin.agenda-config")
public class AgendaFileProperties {
    /** 是否注册 file_based Provider（只读） */
    private boolean fileEnabled = false;
    /** classpath 或绝对路径 JSON/YAML */
    private String filePath = "";
}
