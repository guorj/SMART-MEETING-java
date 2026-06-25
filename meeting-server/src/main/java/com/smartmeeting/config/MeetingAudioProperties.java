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

    /** 是否在离线 ASR 前标准化音频到 16k mono s16le。 */
    private boolean normalizeEnabled = true;
    /** 标准化输出文件名后缀（相对原始 basename）。 */
    private String normalizedSuffix = ".normalized.16k-mono.pcm";
    /** ffmpeg 可执行路径；容器/WAV/MP3 转码时使用。 */
    private String ffmpegPath = "ffmpeg";
    /** ffmpeg 单次转码超时（秒）。 */
    private int ffmpegTimeoutSec = 120;
    /** 质量检测：最短可用时长（毫秒）。 */
    private int qualityMinDurationMs = 1000;
    /** 质量检测：最小峰值振幅（16bit）。 */
    private int qualityMinAbsmax = 100;
    /** 质量检测：最小 RMS。 */
    private double qualityMinRms = 10.0;
}
