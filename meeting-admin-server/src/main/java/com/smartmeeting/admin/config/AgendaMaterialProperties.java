package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会序本地上传资料存储配置（与 meeting-server 共用目录）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.agenda-material")
public class AgendaMaterialProperties {
    private String storageDir = "./data/agenda-materials";
    private long maxImageBytes = 10 * 1024 * 1024L;
    private long maxDocBytes = 20 * 1024 * 1024L;
}
