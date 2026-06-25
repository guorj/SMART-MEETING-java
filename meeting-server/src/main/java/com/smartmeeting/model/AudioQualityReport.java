package com.smartmeeting.model;

import com.smartmeeting.enums.AudioQualityStatus;
import lombok.Builder;
import lombok.Value;

/**
 * 标准化 PCM 质量探测结果。
 */
@Value
@Builder
public class AudioQualityReport {

    int durationMs;
    double rms;
    int absmax;
    double nonzeroRatio;
    AudioQualityStatus status;
    String message;

    public boolean usableForAsr() {
        return status == AudioQualityStatus.OK;
    }
}
