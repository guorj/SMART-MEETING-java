package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议 ASR 相关配置，绑定 {@code meeting.asr.*} 中与业务开关相关的项。
 * <p>
 * 讯飞连接参数仍由 {@code meeting.asr.xfyun.*} 等 {@link org.springframework.beans.factory.annotation.Value} 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.asr")
public class MeetingAsrProperties {

    /**
     * 是否启用会中实时转写（浏览器 PCM → 讯飞实时 ASR → 字幕/库表）。
     * 关闭后仍缓存录音文件，会后纪要可走离线 ASR。
     */
    private boolean realtimeEnabled = true;
}
