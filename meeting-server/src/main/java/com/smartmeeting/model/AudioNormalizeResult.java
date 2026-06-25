package com.smartmeeting.model;

import com.smartmeeting.enums.AudioQualityStatus;
import lombok.Builder;
import lombok.Value;

/**
 * 音频标准化结果。
 */
@Value
@Builder
public class AudioNormalizeResult {

    String meetingId;
    String originalPath;
    String normalizedPath;
    AudioFormatDescriptor sourceFormat;
    AudioQualityReport quality;
    boolean converted;

    public boolean usableForAsr() {
        return normalizedPath != null
                && !normalizedPath.isBlank()
                && quality != null
                && quality.usableForAsr();
    }

    public AudioQualityStatus qualityStatus() {
        return quality != null ? quality.getStatus() : AudioQualityStatus.CONVERT_FAILED;
    }
}
