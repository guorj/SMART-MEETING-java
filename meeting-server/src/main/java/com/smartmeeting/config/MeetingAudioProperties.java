package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议录音 PCM 基础设施配置，绑定 {@code meeting.audio.*}（须重启生效）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.audio")
public class MeetingAudioProperties {

    /** 讯飞协议默认 16kHz；修改须重启并自担兼容风险。 */
    private int sampleRate = 16000;
    private int bitDepth = 16;
    private int channels = 1;
    private int frameDurationMs = 40;
    private int maxDurationHours = 4;
    private int silenceAutoPauseMin = 30;
    private int cacheRetentionHours = 72;
    private String cacheDir = "./data/audio";
    private boolean cloudDownloadEnabled = true;
    private int queueMaxsize = 100;
    private int writeFlushInterval = 25;
}
