package com.smartmeeting.model;

import com.smartmeeting.enums.AudioSourceType;
import lombok.Builder;
import lombok.Value;

/**
 * 原始音频格式描述（raw PCM 必须显式或 sidecar 提供，不能靠文件猜测）。
 */
@Value
@Builder
public class AudioFormatDescriptor {

    public static final String ENCODING_PCM_S16LE = "pcm_s16le";

    String encoding;
    int sampleRate;
    int channels;
    int bitDepth;
    AudioSourceType sourceType;

    public static AudioFormatDescriptor standardBrowserDefault() {
        return AudioFormatDescriptor.builder()
                .encoding(ENCODING_PCM_S16LE)
                .sampleRate(16000)
                .channels(1)
                .bitDepth(16)
                .sourceType(AudioSourceType.MICROPHONE)
                .build();
    }

    public boolean isStandardAsrFormat(int targetRate, int targetChannels, int targetBitDepth) {
        return ENCODING_PCM_S16LE.equalsIgnoreCase(encoding)
                && sampleRate == targetRate
                && channels == targetChannels
                && bitDepth == targetBitDepth;
    }

    public int bytesPerSecond() {
        return Math.max(1, sampleRate) * Math.max(1, channels) * Math.max(1, bitDepth) / 8;
    }
}
