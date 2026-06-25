package com.smartmeeting.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.enums.AudioSourceType;
import com.smartmeeting.model.AudioFormatDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取 PCM 旁路元信息文件 {@code {audio}.meta.json}。
 */
@Component
@RequiredArgsConstructor
public class AudioSidecarReader {

    private final ObjectMapper objectMapper;

    public AudioFormatDescriptor readIfPresent(Path audioPath) {
        if (audioPath == null) {
            return null;
        }
        Path sidecar = sidecarPathFor(audioPath);
        if (!Files.isRegularFile(sidecar)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(sidecar.toFile());
            String encoding = textOrDefault(root, "encoding", AudioFormatDescriptor.ENCODING_PCM_S16LE);
            int sampleRate = root.path("sampleRate").asInt(16000);
            int channels = root.path("channels").asInt(1);
            int bitDepth = root.path("bitDepth").asInt(16);
            AudioSourceType sourceType = parseSourceType(textOrDefault(root, "sourceType", "UNKNOWN"));
            if (sampleRate <= 0 || channels <= 0 || bitDepth <= 0) {
                return null;
            }
            return AudioFormatDescriptor.builder()
                    .encoding(encoding)
                    .sampleRate(sampleRate)
                    .channels(channels)
                    .bitDepth(bitDepth)
                    .sourceType(sourceType)
                    .build();
        } catch (IOException e) {
            return null;
        }
    }

    public static Path sidecarPathFor(Path audioPath) {
        String fileName = audioPath.getFileName().toString();
        return audioPath.getParent().resolve(fileName + ".meta.json");
    }

    private static String textOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String value = node.asText("");
        return value.isBlank() ? defaultValue : value.trim();
    }

    private static AudioSourceType parseSourceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AudioSourceType.UNKNOWN;
        }
        try {
            return AudioSourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return AudioSourceType.UNKNOWN;
        }
    }
}
