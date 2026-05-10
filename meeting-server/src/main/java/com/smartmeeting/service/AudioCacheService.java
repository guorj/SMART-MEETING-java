package com.smartmeeting.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

@Slf4j
@Service
public class AudioCacheService {

    @Value("${meeting.audio.cache-dir:/data/audio}")
    private String cacheDir;

    @Value("${meeting.audio.cache-retention-hours:168}")
    private int retentionHours;

    public void writeAudioChunk(String meetingId, byte[] pcmData) {
        String dateDir = java.time.LocalDate.now().toString();
        Path filePath = Paths.get(cacheDir, dateDir, meetingId + ".pcm");
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, pcmData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write audio chunk for meeting: {}", meetingId, e);
        }
    }

    public byte[] readAudio(String meetingId) throws IOException {
        String dateDir = java.time.LocalDate.now().toString();
        Path filePath = Paths.get(cacheDir, dateDir, meetingId + ".pcm");
        return Files.readAllBytes(filePath);
    }

    public void cleanExpiredCache() {
        long cutoff = System.currentTimeMillis() - (retentionHours * 3600_000L);
        Path basePath = Paths.get(cacheDir);
        if (!Files.exists(basePath)) return;

        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() < cutoff;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            log.info("Deleted expired audio: {}", p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk audio cache dir", e);
        }
    }
}
