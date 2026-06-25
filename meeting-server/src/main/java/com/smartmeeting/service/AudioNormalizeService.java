package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.enums.AudioAssetRole;
import com.smartmeeting.enums.AudioQualityStatus;
import com.smartmeeting.enums.AudioSourceType;
import com.smartmeeting.model.AudioFormatDescriptor;
import com.smartmeeting.model.AudioNormalizeResult;
import com.smartmeeting.model.AudioQualityReport;
import com.smartmeeting.util.AudioSidecarReader;
import com.smartmeeting.util.RawPcmConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 多音源音频标准化：统一输出 16kHz / mono / s16le PCM，并记录资产与质量指标。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioNormalizeService {

    private final MeetingAudioProperties audioProperties;
    private final MeetingAudioAssetService meetingAudioAssetService;
    private final AudioQualityProbe audioQualityProbe;
    private final AudioSidecarReader audioSidecarReader;
    private final FfmpegAudioConverter ffmpegAudioConverter;

    public AudioNormalizeResult normalize(String meetingId, String rawPath) {
        return normalize(meetingId, rawPath, null);
    }

    public AudioNormalizeResult normalize(String meetingId, String rawPath, AudioFormatDescriptor overrideFormat) {
        if (!audioProperties.isNormalizeEnabled()) {
            return passthroughWithoutNormalize(meetingId, rawPath);
        }
        if (rawPath == null || rawPath.isBlank()) {
            return failed(meetingId, rawPath, null, AudioQualityStatus.CONVERT_FAILED, "原始音频路径为空");
        }
        Path input = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(input)) {
            return failed(meetingId, rawPath, null, AudioQualityStatus.CONVERT_FAILED, "原始音频文件不存在");
        }

        AudioFormatDescriptor sourceFormat = resolveSourceFormat(input, overrideFormat);
        long originalSize;
        try {
            originalSize = Files.size(input);
        } catch (IOException e) {
            return failed(meetingId, rawPath, sourceFormat, AudioQualityStatus.CONVERT_FAILED, e.getMessage());
        }

        int originalDurationMs = RawPcmConverter.estimateDurationMs(originalSize, sourceFormat);
        meetingAudioAssetService.saveAsset(
                meetingId,
                AudioAssetRole.ORIGINAL,
                sourceFormat.getSourceType(),
                input.toString(),
                sourceFormat,
                originalSize,
                originalDurationMs,
                null);

        Path output = normalizedOutputPath(input);
        boolean converted;
        try {
            converted = convertToStandard(input, sourceFormat, output);
        } catch (Exception e) {
            log.warn("Audio normalize failed: meetingId={}, input={}, err={}", meetingId, input, e.getMessage());
            AudioQualityReport quality = AudioQualityReport.builder()
                    .durationMs(0)
                    .rms(0)
                    .absmax(0)
                    .nonzeroRatio(0)
                    .status(AudioQualityStatus.CONVERT_FAILED)
                    .message(e.getMessage())
                    .build();
            meetingAudioAssetService.saveAsset(
                    meetingId, AudioAssetRole.NORMALIZED, sourceFormat.getSourceType(),
                    output.toString(), standardFormat(sourceFormat.getSourceType()), 0, 0, quality);
            return AudioNormalizeResult.builder()
                    .meetingId(meetingId)
                    .originalPath(input.toString())
                    .normalizedPath(null)
                    .sourceFormat(sourceFormat)
                    .quality(quality)
                    .converted(false)
                    .build();
        }

        AudioQualityReport quality;
        try {
            quality = audioQualityProbe.probe(output);
            quality = validateDurationConsistency(originalDurationMs, quality, sourceFormat);
        } catch (IOException e) {
            quality = AudioQualityReport.builder()
                    .durationMs(0)
                    .rms(0)
                    .absmax(0)
                    .nonzeroRatio(0)
                    .status(AudioQualityStatus.CONVERT_FAILED)
                    .message(e.getMessage())
                    .build();
        }

        long normalizedSize = 0;
        try {
            normalizedSize = Files.size(output);
        } catch (IOException ignored) {
            // keep 0
        }
        meetingAudioAssetService.saveAsset(
                meetingId,
                AudioAssetRole.NORMALIZED,
                sourceFormat.getSourceType(),
                output.toString(),
                standardFormat(sourceFormat.getSourceType()),
                normalizedSize,
                quality.getDurationMs(),
                quality);

        log.info("Audio normalized: meetingId={}, source={}Hz/{}ch, output={}, durationMs={}, quality={}",
                meetingId, sourceFormat.getSampleRate(), sourceFormat.getChannels(),
                output, quality.getDurationMs(), quality.getStatus());

        return AudioNormalizeResult.builder()
                .meetingId(meetingId)
                .originalPath(input.toString())
                .normalizedPath(output.toString())
                .sourceFormat(sourceFormat)
                .quality(quality)
                .converted(converted)
                .build();
    }

    public String resolveNormalizedPath(String meetingId, String rawPath) {
        AudioNormalizeResult result = normalize(meetingId, rawPath);
        return result.usableForAsr() ? result.getNormalizedPath() : null;
    }

    private AudioNormalizeResult passthroughWithoutNormalize(String meetingId, String rawPath) {
        AudioQualityReport quality;
        try {
            quality = audioQualityProbe.probe(Paths.get(rawPath));
        } catch (IOException e) {
            quality = AudioQualityReport.builder()
                    .durationMs(0)
                    .rms(0)
                    .absmax(0)
                    .nonzeroRatio(0)
                    .status(AudioQualityStatus.CONVERT_FAILED)
                    .message(e.getMessage())
                    .build();
        }
        return AudioNormalizeResult.builder()
                .meetingId(meetingId)
                .originalPath(rawPath)
                .normalizedPath(rawPath)
                .sourceFormat(AudioFormatDescriptor.standardBrowserDefault())
                .quality(quality)
                .converted(false)
                .build();
    }

    private AudioFormatDescriptor resolveSourceFormat(Path input, AudioFormatDescriptor overrideFormat) {
        if (overrideFormat != null) {
            return overrideFormat;
        }
        AudioFormatDescriptor sidecar = audioSidecarReader.readIfPresent(input);
        if (sidecar != null) {
            return sidecar;
        }
        String lower = input.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.contains("-cloud") || lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return AudioFormatDescriptor.builder()
                    .encoding("m4a")
                    .sampleRate(0)
                    .channels(0)
                    .bitDepth(16)
                    .sourceType(AudioSourceType.CLOUD)
                    .build();
        }
        return AudioFormatDescriptor.builder()
                .encoding(AudioFormatDescriptor.ENCODING_PCM_S16LE)
                .sampleRate(audioProperties.getSampleRate())
                .channels(audioProperties.getChannels())
                .bitDepth(audioProperties.getBitDepth())
                .sourceType(AudioSourceType.MICROPHONE)
                .build();
    }

    private boolean convertToStandard(Path input, AudioFormatDescriptor source, Path output) throws Exception {
        Files.createDirectories(output.getParent());
        if (source.isStandardAsrFormat(
                RawPcmConverter.TARGET_SAMPLE_RATE,
                RawPcmConverter.TARGET_CHANNELS,
                RawPcmConverter.TARGET_BIT_DEPTH)) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
        if (ffmpegAudioConverter.supportsContainer(input) && ffmpegAudioConverter.isAvailable()) {
            ffmpegAudioConverter.convertToStandardPcm(input, source, output);
            return true;
        }
        if (AudioFormatDescriptor.ENCODING_PCM_S16LE.equalsIgnoreCase(source.getEncoding())
                && source.getBitDepth() == 16) {
            byte[] converted = RawPcmConverter.convertFile(input, source);
            Files.write(output, converted);
            return true;
        }
        if (ffmpegAudioConverter.isAvailable()) {
            ffmpegAudioConverter.convertToStandardPcm(input, source, output);
            return true;
        }
        throw new IOException("No converter available for format "
                + source.getEncoding() + " " + source.getSampleRate() + "Hz/" + source.getChannels() + "ch");
    }

    private Path normalizedOutputPath(Path input) {
        String fileName = input.getFileName().toString();
        String suffix = audioProperties.getNormalizedSuffix();
        if (fileName.endsWith(".pcm")) {
            fileName = fileName.substring(0, fileName.length() - 4) + suffix;
        } else {
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            fileName = base + suffix;
        }
        Path parent = input.getParent();
        return parent != null ? parent.resolve(fileName) : Paths.get(fileName);
    }

    private AudioQualityReport validateDurationConsistency(int originalDurationMs,
                                                           AudioQualityReport quality,
                                                           AudioFormatDescriptor source) {
        if (quality.getStatus() != AudioQualityStatus.OK || originalDurationMs <= 0) {
            return quality;
        }
        int normalizedDurationMs = quality.getDurationMs();
        int toleranceMs = Math.max(500, originalDurationMs / 10);
        if (Math.abs(normalizedDurationMs - originalDurationMs) > toleranceMs
                && source.getSampleRate() > 0 && source.getChannels() > 0) {
            return AudioQualityReport.builder()
                    .durationMs(normalizedDurationMs)
                    .rms(quality.getRms())
                    .absmax(quality.getAbsmax())
                    .nonzeroRatio(quality.getNonzeroRatio())
                    .status(AudioQualityStatus.FORMAT_UNKNOWN)
                    .message("标准化后时长与原始元信息偏差过大，可能格式元信息错误")
                    .build();
        }
        return quality;
    }

    private static AudioFormatDescriptor standardFormat(AudioSourceType sourceType) {
        return AudioFormatDescriptor.builder()
                .encoding(AudioFormatDescriptor.ENCODING_PCM_S16LE)
                .sampleRate(RawPcmConverter.TARGET_SAMPLE_RATE)
                .channels(RawPcmConverter.TARGET_CHANNELS)
                .bitDepth(RawPcmConverter.TARGET_BIT_DEPTH)
                .sourceType(sourceType != null ? sourceType : AudioSourceType.UNKNOWN)
                .build();
    }

    private AudioNormalizeResult failed(String meetingId,
                                        String rawPath,
                                        AudioFormatDescriptor sourceFormat,
                                        AudioQualityStatus status,
                                        String message) {
        AudioQualityReport quality = AudioQualityReport.builder()
                .durationMs(0)
                .rms(0)
                .absmax(0)
                .nonzeroRatio(0)
                .status(status)
                .message(message)
                .build();
        return AudioNormalizeResult.builder()
                .meetingId(meetingId)
                .originalPath(rawPath)
                .normalizedPath(null)
                .sourceFormat(sourceFormat)
                .quality(quality)
                .converted(false)
                .build();
    }
}
