package com.smartmeeting.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 音频缓存清理定时任务
 *
 * 规则:
 * - 每天凌晨3点执行
 * - 清理 /data/audio/ 下超过7天的 PCM 文件
 * - 仅清理已完成/已终止状态会议的音频（保留处理中状态的）
 *
 * 原因:
 * - 音频文件占用存储空间
 * - 已完成会议的音频可从云端恢复
 * - 处理中状态会议的音频需要保留供后续分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioCacheCleaner {

    private final MeetingMapper meetingMapper;

    @Value("${meeting.audio.cache-dir:/data/audio}")
    private String cacheDir;

    @Value("${meeting.audio.cache-retention-hours:168}")
    private int retentionHours; // 默认7天

    /**
     * 每天凌晨3点清理过期音频缓存
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanCache() {
        log.info("Running audio cache cleanup: dir={}, retention={}h", cacheDir, retentionHours);

        Path audioPath = Paths.get(cacheDir);
        if (!Files.exists(audioPath)) {
            log.warn("Audio cache directory not found: {}", cacheDir);
            return;
        }

        try {
            // 查找所有音频文件
            List<Path> audioFiles = Files.walk(audioPath)
                    .filter(p -> p.toString().endsWith(".pcm"))
                    .collect(Collectors.toList());

            log.info("Found {} audio files in cache", audioFiles.size());

            long retentionThreshold = System.currentTimeMillis() - (retentionHours * 60 * 60 * 1000L);
            int deletedCount = 0;
            long deletedSize = 0;

            for (Path file : audioFiles) {
                try {
                    // 检查文件修改时间
                    long fileTime = Files.getLastModifiedTime(file).toMillis();
                    if (fileTime < retentionThreshold) {
                        // 提取会议ID（文件名格式：{meetingId}.pcm 或 {date}/{meetingId}.pcm）
                        String filename = file.getFileName().toString();
                        String meetingId = filename.replace(".pcm", "");

                        // 检查会议状态（仅清理已完成/已终止的）
                        Meeting meeting = meetingMapper.selectById(meetingId);
                        if (meeting == null) {
                            // 会议不存在，直接删除
                            log.debug("Deleting orphan audio file: {}", file);
                            long size = Files.size(file);
                            Files.delete(file);
                            deletedCount++;
                            deletedSize += size;
                            continue;
                        }

                        String status = meeting.getStatus();
                        if (status.equals(MeetingStatus.COMPLETED.name()) ||
                            status.equals(MeetingStatus.ALL_DONE.name()) ||
                            status.equals(MeetingStatus.ARCHIVED.name()) ||
                            status.equals(MeetingStatus.ABORTED.name()) ||
                            status.equals(MeetingStatus.CANCELLED.name())) {

                            log.debug("Deleting audio for completed meeting: id={}, file={}", meetingId, file);
                            long size = Files.size(file);
                            Files.delete(file);
                            deletedCount++;
                            deletedSize += size;

                            // 更新会议记录的 audioPath 为空（可选）
                            meeting.setAudioPath(null);
                            meetingMapper.updateById(meeting);

                        } else {
                            log.debug("Skipping audio for in-progress meeting: id={}, status={}", meetingId, status);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to process audio file: {}, error={}", file, e.getMessage());
                }
            }

            log.info("Audio cache cleanup completed: deleted {} files, freed {} MB",
                    deletedCount, deletedSize / (1024 * 1024));

            // 清理空目录
            cleanupEmptyDirectories(audioPath);

        } catch (Exception e) {
            log.error("Audio cache cleanup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理空目录
     */
    private void cleanupEmptyDirectories(Path basePath) throws Exception {
        Files.walk(basePath)
                .filter(Files::isDirectory)
                .filter(dir -> {
                    try {
                        return Files.list(dir).findAny().isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .forEach(dir -> {
                    try {
                        if (!dir.equals(basePath)) {
                            Files.delete(dir);
                            log.debug("Deleted empty directory: {}", dir);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to delete empty directory: {}", dir);
                    }
                });
    }

    /**
     * 每周报告缓存使用情况
     */
    @Scheduled(cron = "0 0 3 ? * SUN") // 每周日凌晨3点
    public void reportCacheUsage() {
        Path audioPath = Paths.get(cacheDir);
        if (!Files.exists(audioPath)) {
            return;
        }

        try {
            long totalSize = Files.walk(audioPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .sum();

            long fileCount = Files.walk(audioPath)
                    .filter(Files::isRegularFile)
                    .count();

            log.info("Audio cache usage: {} files, {} MB", fileCount, totalSize / (1024 * 1024));

        } catch (Exception e) {
            log.warn("Failed to calculate cache usage: {}", e.getMessage());
        }
    }
}