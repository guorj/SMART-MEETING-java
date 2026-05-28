package com.smartmeeting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * 音频来源物化服务：将云端 URL 拉取为本地文件，供离线 ASR 使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAudioMaterializerService {

    private final RestTemplate restTemplate;

    @Value("${meeting.audio.cache-dir:./data/audio}")
    private String audioCacheDir;

    @Value("${meeting.audio.cloud-download-enabled:true}")
    private boolean cloudDownloadEnabled;

    /**
     * 解析可用音频路径。
     * <ul>
     *     <li>本地路径：直接返回</li>
     *     <li>云端 URL：下载到本地后返回</li>
     *     <li>都不可用：返回空串</li>
     * </ul>
     */
    public String materialize(String meetingId, String preferredPathOrUrl, String fallbackCloudUrl) {
        if (isLocalPath(preferredPathOrUrl)) {
            return preferredPathOrUrl;
        }
        String cloudUrl = isHttpUrl(preferredPathOrUrl) ? preferredPathOrUrl : fallbackCloudUrl;
        if (!isHttpUrl(cloudUrl)) {
            return "";
        }
        if (!cloudDownloadEnabled) {
            log.info("Cloud download disabled, skip cloud audio materialization: meetingId={}", meetingId);
            return "";
        }
        return downloadCloudAudio(meetingId, cloudUrl);
    }

    private String downloadCloudAudio(String meetingId, String cloudUrl) {
        try {
            String ext = guessExtension(cloudUrl);
            Path targetDir = Paths.get(audioCacheDir, LocalDate.now().toString(), "cloud");
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(meetingId + "-cloud" + ext);

            restTemplate.execute(cloudUrl, HttpMethod.GET, null, response -> {
                try (InputStream in = response.getBody()) {
                    if (in == null) {
                        return null;
                    }
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    return null;
                }
            });

            if (Files.exists(target) && Files.size(target) > 0) {
                log.info("Cloud audio materialized: meetingId={}, url={}, path={}", meetingId, cloudUrl, target);
                return target.toString();
            }
            log.warn("Cloud audio download returned empty file: meetingId={}, url={}", meetingId, cloudUrl);
        } catch (Exception e) {
            log.warn("Failed to materialize cloud audio: meetingId={}, url={}, err={}",
                    meetingId, cloudUrl, e.getMessage());
        }
        return "";
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private boolean isLocalPath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !isHttpUrl(value) && Files.exists(Paths.get(value));
    }

    private String guessExtension(String cloudUrl) {
        String lower = cloudUrl == null ? "" : cloudUrl.toLowerCase();
        if (lower.contains(".mp4")) {
            return ".mp4";
        }
        if (lower.contains(".wav")) {
            return ".wav";
        }
        return ".m4a";
    }
}
