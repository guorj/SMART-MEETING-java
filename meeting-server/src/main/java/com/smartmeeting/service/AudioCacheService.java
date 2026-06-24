package com.smartmeeting.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 会议 PCM 音频本地缓存服务。
 *
 * <p>按「缓存根目录 / 日期 / {meetingId}.pcm」追加写入实时音频块，支持按会议读取整段 PCM，
 * 并依据 {@code meeting.audio.cache-retention-hours} 清理过期文件。
 */
@Slf4j
@Service
public class AudioCacheService {

    @Value("${meeting.audio.cache-dir:/data/audio}")
    private String cacheDir;

    @Value("${meeting.audio.cache-retention-hours:168}")
    private int retentionHours;

    /**
     * 将 PCM 音频块追加写入当日会议缓存文件。
     *
     * @param meetingId 会议 ID（用于文件名）
     * @param pcmData   PCM 原始字节
     */
    /**
     * 按约定目录结构生成 PCM 路径（不校验文件是否存在）。
     *
     * @param meetingId 会议 ID
     * @param date      日期分区
     * @return 与 {@link RecordingService} 一致的相对/绝对路径字符串
     */
    public String cachePathFor(String meetingId, LocalDate date) {
        return cacheDir + "/" + date + "/" + meetingId + ".pcm";
    }

    /**
     * 查找已落盘的 PCM（先当日、再前一日，应对跨零点场景）。
     *
     * @param meetingId 会议 ID
     * @return 非空且 size&gt;0 的文件路径
     */
    public Optional<String> findExistingCachePath(String meetingId) {
        LocalDate today = LocalDate.now();
        for (LocalDate date : new LocalDate[]{today, today.minusDays(1)}) {
            Path filePath = Paths.get(cachePathFor(meetingId, date));
            try {
                if (Files.isRegularFile(filePath) && Files.size(filePath) > 0) {
                    return Optional.of(filePath.toString());
                }
            } catch (IOException e) {
                log.debug("Skip unreadable cache file: {}", filePath, e);
            }
        }
        return Optional.empty();
    }

    public void writeAudioChunk(String meetingId, byte[] pcmData) {
        Path filePath = Paths.get(cachePathFor(meetingId, LocalDate.now()));
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, pcmData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write audio chunk for meeting: {}", meetingId, e);
        }
    }

    /**
     * 读取当日会议对应的完整 PCM 缓存文件。
     *
     * @param meetingId 会议 ID
     * @return 文件全部字节
     * @throws IOException 文件不存在或读失败时
     */
    public byte[] readAudio(String meetingId) throws IOException {
        Path filePath = Paths.get(cachePathFor(meetingId, LocalDate.now()));
        return Files.readAllBytes(filePath);
    }

    /**
     * 遍历缓存目录，删除最后修改时间早于保留期的普通文件。
     */
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
