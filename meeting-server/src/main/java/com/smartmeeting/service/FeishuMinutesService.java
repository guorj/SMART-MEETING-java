package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingAudioProperties;
import com.smartmeeting.enums.AudioSourceType;
import com.smartmeeting.model.AudioFormatDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书妙记（Minutes）Open API 客户端。
 * <p>
 * 封装妙记元数据查询、音视频下载链接获取、下载 + ffmpeg 转码为 16k mono s16le PCM。
 * 元数据接口使用 {@link FeishuService#getTenantToken()}；
 * 媒体下载<strong>仅</strong>使用 {@link FeishuMinutesUserTokenProvider}（{@code user_access_token}）。
 * <p>
 * 参考实现：{@code jq-openclaw/services/feishu-router/fetch-minutes.mjs}（Node 版，已验证可用）。
 * <p>
 * 飞书 API 文档：
 * <ul>
 *   <li>元数据: GET /minutes/v1/minutes/{token}</li>
 *   <li>媒体下载: GET /minutes/v1/minutes/{token}/media → 返回 download_url（有效期 1 天）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuMinutesService {

    private static final Pattern MINUTE_TOKEN_FROM_URL = Pattern.compile("/minutes/([A-Za-z0-9]+)");

    private final FeishuService feishuService;
    private final FeishuMinutesUserTokenProvider minutesUserTokenProvider;
    private final ObjectMapper objectMapper;
    private final FfmpegAudioConverter ffmpegAudioConverter;
    private final MeetingAudioProperties audioProperties;
    private final RestTemplate restTemplate;

    @Value("${meeting.feishu.base-url:https://open.feishu.cn}")
    private String feishuBaseUrl;

    @Value("${meeting.audio.cache-dir:./data/audio}")
    private String audioCacheDir;

    /** 妙记元数据。 */
    public record MinuteMeta(String token, String title, String ownerId, long durationMs,
                             String createTime, String url) {
    }

    /**
     * 从妙记页面 URL 提取 minute_token。
     * <p>
     * URL 格式：{@code https://xxx.feishu.cn/minutes/{token}}
     *
     * @return 提取成功返回 token；失败返回空串
     */
    public static String extractMinuteToken(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        Matcher m = MINUTE_TOKEN_FROM_URL.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /**
     * 获取妙记元数据。
     *
     * @param minuteToken 妙记 token（24 字符）
     * @return 元数据；调用失败返回 null
     */
    public MinuteMeta getMinuteMeta(String minuteToken) {
        try {
            String token = feishuService.getTenantToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    feishuBaseUrl + "/open-apis/minutes/v1/minutes/" + minuteToken,
                    HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root == null || root.path("code").asInt(0) != 0) {
                String msg = root == null ? "empty body" : root.path("msg").asText("unknown");
                int code = root == null ? -1 : root.path("code").asInt(-1);
                log.warn("FeishuMinutes getMinuteMeta failed: token={}, code={}, msg={}", minuteToken, code, msg);
                return null;
            }
            JsonNode minute = root.path("data").path("minute");
            return new MinuteMeta(
                    minute.path("token").asText(""),
                    minute.path("title").asText(""),
                    minute.path("owner_id").asText(""),
                    minute.path("duration").asLong(0),
                    minute.path("create_time").asText(""),
                    minute.path("url").asText(""));
        } catch (Exception e) {
            log.warn("FeishuMinutes getMinuteMeta exception: token={}, err={}", minuteToken, e.getMessage());
            return null;
        }
    }

    /**
     * 获取妙记音视频下载链接（有效期约 1 天）。
     * <p>
     * 仅使用 {@code user_access_token}（与飞书 API 调试台一致）；未配置用户 token 时不回退 tenant。
     *
     * @param minuteToken 妙记 token
     * @return download_url；调用失败或未配置用户 token 时返回 null
     */
    public String getMediaDownloadUrl(String minuteToken) {
        if (!minutesUserTokenProvider.isConfigured()) {
            log.warn("FeishuMinutes media download requires user_access_token; configure "
                    + "meeting.feishu.minutes.user-refresh-token or user-access-token");
            return null;
        }
        String userToken = minutesUserTokenProvider.getUserAccessToken();
        if (userToken == null || userToken.isBlank()) {
            log.warn("FeishuMinutes user_access_token unavailable (refresh failed?); token={}", minuteToken);
            return null;
        }
        return requestMediaDownloadUrl(minuteToken, userToken, "user_access_token");
    }

    private String requestMediaDownloadUrl(String minuteToken, String accessToken, String tokenKind) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    feishuBaseUrl + "/open-apis/minutes/v1/minutes/" + minuteToken + "/media",
                    HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root == null || root.path("code").asInt(0) != 0) {
                String msg = root == null ? "empty body" : root.path("msg").asText("unknown");
                int code = root == null ? -1 : root.path("code").asInt(-1);
                log.warn("FeishuMinutes getMediaDownloadUrl failed: token={}, auth={}, code={}, msg={}",
                        minuteToken, tokenKind, code, msg);
                return null;
            }
            String url = root.path("data").path("download_url").asText("");
            if (!url.isBlank()) {
                log.info("FeishuMinutes getMediaDownloadUrl ok: token={}, auth={}", minuteToken, tokenKind);
            }
            return url.isBlank() ? null : url;
        } catch (Exception e) {
            log.warn("FeishuMinutes getMediaDownloadUrl exception: token={}, auth={}, err={}",
                    minuteToken, tokenKind, e.getMessage());
            return null;
        }
    }

    /**
     * 下载妙记音视频并转码为 16k mono s16le PCM（{meetingId}_vc.pcm）。
     * <p>
     * 流程：
     * <ol>
     *   <li>GET /media 拿 download_url</li>
     *   <li>HTTP GET download_url → 临时 m4a/mp4</li>
     *   <li>ffmpeg 转码 → {meetingId}_vc.pcm</li>
     *   <li>删除临时容器文件</li>
     * </ol>
     *
     * @param meetingId   会议 ID（用于命名输出文件）
     * @param minuteToken 妙记 token
     * @return PCM 文件路径；失败返回 null
     */
    public Path downloadAndTranscodeToPcm(String meetingId, String minuteToken) {
        String downloadUrl = getMediaDownloadUrl(minuteToken);
        if (downloadUrl == null) {
            log.warn("FeishuMinutes download skipped: no media url, meetingId={}, token={}", meetingId, minuteToken);
            return null;
        }

        Path outputDir = Paths.get(audioCacheDir, LocalDate.now().toString());
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.warn("FeishuMinutes create dir failed: {}", outputDir);
            return null;
        }

        String ext = guessExtension(downloadUrl);
        Path rawContainer = outputDir.resolve(meetingId + "_vc_raw." + ext);
        Path pcmOutput = outputDir.resolve(meetingId + "_vc.pcm");

        // 1. 下载容器文件
        try {
            restTemplate.execute(downloadUrl, HttpMethod.GET, null, response -> {
                try (InputStream in = response.getBody()) {
                    if (in == null) {
                        return null;
                    }
                    Files.copy(in, rawContainer, StandardCopyOption.REPLACE_EXISTING);
                    return null;
                }
            });
            if (!Files.exists(rawContainer) || Files.size(rawContainer) == 0) {
                log.warn("FeishuMinutes download empty: meetingId={}, url={}", meetingId, downloadUrl);
                return null;
            }
            log.info("FeishuMinutes downloaded: meetingId={}, file={}, size={}bytes",
                    meetingId, rawContainer, Files.size(rawContainer));
        } catch (Exception e) {
            log.warn("FeishuMinutes download failed: meetingId={}, url={}, err={}",
                    meetingId, downloadUrl, e.getMessage());
            return null;
        }

        // 2. ffmpeg 转码为标准 PCM
        try {
            AudioFormatDescriptor source = AudioFormatDescriptor.builder()
                    .encoding(ext)
                    .sampleRate(0)
                    .channels(0)
                    .bitDepth(16)
                    .sourceType(AudioSourceType.CLOUD)
                    .build();
            ffmpegAudioConverter.convertToStandardPcm(rawContainer, source, pcmOutput);
            log.info("FeishuMinutes transcode ok: meetingId={}, pcm={}, size={}bytes",
                    meetingId, pcmOutput, Files.size(pcmOutput));
            return pcmOutput;
        } catch (Exception e) {
            log.warn("FeishuMinutes transcode failed: meetingId={}, raw={}, err={}",
                    meetingId, rawContainer, e.getMessage());
            return null;
        } finally {
            // 3. 删除临时容器文件
            try {
                Files.deleteIfExists(rawContainer);
            } catch (IOException ignored) {
                // 删除失败不影响主流程
            }
        }
    }

    private static String guessExtension(String url) {
        Matcher m = Pattern.compile("\\.(mp3|m4a|wav|aac|mp4|ogg)(\\?|$)", Pattern.CASE_INSENSITIVE).matcher(url);
        if (m.find()) {
            return m.group(1).toLowerCase(Locale.ROOT);
        }
        return "m4a";
    }
}
