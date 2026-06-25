package com.smartmeeting.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议音频资产，对应 {@code int_meeting_audio_asset}。
 * <p>
 * 记录原始录音与标准化后 PCM 的路径、格式元信息与质量指标。
 */
@Data
@TableName("int_meeting_audio_asset")
public class MeetingAudioAsset {
    @TableId
    private String id;
    private String meetingId;
    /** ORIGINAL | NORMALIZED */
    private String assetRole;
    /** MICROPHONE | STEREO_MIX | UPLOAD | CLOUD | UNKNOWN */
    private String sourceType;
    private String path;
    private String encoding;
    private Integer sampleRate;
    private Integer channels;
    private Integer bitDepth;
    private Long fileSize;
    private Integer durationMs;
    private Double rms;
    private Integer absmax;
    private Double nonzeroRatio;
    /** OK | LOW_VOLUME | SILENT | FORMAT_UNKNOWN | CONVERT_FAILED | TOO_SHORT */
    private String qualityStatus;
    private LocalDateTime createdAt;
}
