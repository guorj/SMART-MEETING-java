package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 主持运行时 timing/chunk 配置，绑定 {@code meeting.host.runtime.*}（须重启生效）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.host.runtime")
public class MeetingHostRuntimeProperties {

    private int ttsChunkBytes = 6000;
    private long ttsClientPlaybackTailMs = 600L;
    private long rollCallArmExtraMs = 300L;
    private int hostReminderToastMs = 3000;
    private int rollCallWindowFloorSec = 5;
    private int rollCallOnlineInventoryFloorSec = 15;
}
