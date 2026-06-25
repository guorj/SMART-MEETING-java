package com.smartmeeting.service;

import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.model.AudioFormatDescriptor;
import com.smartmeeting.util.RawPcmConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 通过 ffmpeg 解码容器音频或复杂 raw PCM 并输出标准 PCM。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FfmpegAudioConverter {

    private final MeetingAudioProperties audioProperties;

    public boolean isAvailable() {
        try {
            Process process = new ProcessBuilder(audioProperties.getFfmpegPath(), "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean supportsContainer(Path input) {
        String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".m4a")
                || name.endsWith(".mp4") || name.endsWith(".aac") || name.endsWith(".flac");
    }

    public void convertToStandardPcm(Path input, AudioFormatDescriptor source, Path output) throws IOException, InterruptedException {
        Files.createDirectories(output.getParent());
        List<String> command = new ArrayList<>();
        command.add(audioProperties.getFfmpegPath());
        command.add("-y");
        if (isRawPcm(source, input)) {
            command.add("-f");
            command.add("s16le");
            command.add("-ar");
            command.add(String.valueOf(source.getSampleRate()));
            command.add("-ac");
            command.add(String.valueOf(source.getChannels()));
        }
        command.add("-i");
        command.add(input.toString());
        command.add("-ac");
        command.add(String.valueOf(RawPcmConverter.TARGET_CHANNELS));
        command.add("-ar");
        command.add(String.valueOf(RawPcmConverter.TARGET_SAMPLE_RATE));
        command.add("-f");
        command.add("s16le");
        command.add(output.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(Math.max(30, audioProperties.getFfmpegTimeoutSec()), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg timeout converting " + input);
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg failed with exit code " + process.exitValue() + " for " + input);
        }
        if (!Files.isRegularFile(output) || Files.size(output) == 0) {
            throw new IOException("ffmpeg produced empty output for " + input);
        }
    }

    private static boolean isRawPcm(AudioFormatDescriptor source, Path input) {
        if (source == null) {
            return input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pcm");
        }
        return AudioFormatDescriptor.ENCODING_PCM_S16LE.equalsIgnoreCase(source.getEncoding())
                || input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pcm");
    }
}
