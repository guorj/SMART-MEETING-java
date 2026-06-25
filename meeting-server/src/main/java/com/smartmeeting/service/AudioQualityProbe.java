package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.enums.AudioQualityStatus;
import com.smartmeeting.model.AudioQualityReport;
import com.smartmeeting.util.PcmSliceUtil;
import com.smartmeeting.util.RawPcmConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对标准 16k mono s16le PCM 做质量探测。
 */
@Component
@RequiredArgsConstructor
public class AudioQualityProbe {

    private final MeetingAudioProperties audioProperties;

    public AudioQualityReport probe(Path normalizedPcm) throws IOException {
        if (normalizedPcm == null || !Files.isRegularFile(normalizedPcm)) {
            return AudioQualityReport.builder()
                    .durationMs(0)
                    .rms(0)
                    .absmax(0)
                    .nonzeroRatio(0)
                    .status(AudioQualityStatus.CONVERT_FAILED)
                    .message("标准化 PCM 不存在")
                    .build();
        }
        byte[] data = Files.readAllBytes(normalizedPcm);
        int durationMs = PcmSliceUtil.byteLengthToMs(data.length, RawPcmConverter.TARGET_SAMPLE_RATE);
        Stats stats = analyzeS16le(data);
        AudioQualityStatus status = classify(durationMs, stats);
        return AudioQualityReport.builder()
                .durationMs(durationMs)
                .rms(stats.rms)
                .absmax(stats.absmax)
                .nonzeroRatio(stats.nonzeroRatio)
                .status(status)
                .message(messageFor(status))
                .build();
    }

    private AudioQualityStatus classify(int durationMs, Stats stats) {
        if (durationMs < Math.max(1, audioProperties.getQualityMinDurationMs())) {
            return AudioQualityStatus.TOO_SHORT;
        }
        if (stats.absmax < Math.max(1, audioProperties.getQualityMinAbsmax())
                || stats.rms < Math.max(0.0, audioProperties.getQualityMinRms())) {
            if (stats.absmax <= 8 && stats.rms <= 1.0) {
                return AudioQualityStatus.SILENT;
            }
            return AudioQualityStatus.LOW_VOLUME;
        }
        return AudioQualityStatus.OK;
    }

    private static String messageFor(AudioQualityStatus status) {
        return switch (status) {
            case OK -> "音频质量正常";
            case LOW_VOLUME -> "音量偏低，建议检查录音源或增益";
            case SILENT -> "近似静音，无法可靠转写";
            case FORMAT_UNKNOWN -> "格式元信息与文件长度不匹配";
            case CONVERT_FAILED -> "标准化失败";
            case TOO_SHORT -> "录音时长过短";
        };
    }

    static Stats analyzeS16le(byte[] data) {
        if (data == null || data.length < 2) {
            return new Stats(0, 0, 0);
        }
        int usable = data.length - (data.length % 2);
        int sampleCount = usable / 2;
        long sumSquares = 0;
        int absmax = 0;
        int nonzero = 0;
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, usable).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            short sample = buffer.getShort();
            int abs = Math.abs(sample);
            if (sample != 0) {
                nonzero++;
            }
            absmax = Math.max(absmax, abs);
            sumSquares += (long) sample * sample;
        }
        double rms = sampleCount == 0 ? 0 : Math.sqrt((double) sumSquares / sampleCount);
        double nonzeroRatio = sampleCount == 0 ? 0 : (double) nonzero / sampleCount;
        return new Stats(rms, absmax, nonzeroRatio);
    }

    record Stats(double rms, int absmax, double nonzeroRatio) {
    }
}
