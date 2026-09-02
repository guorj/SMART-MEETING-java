package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingFeishuMinutesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 为妙记媒体下载提供 {@code user_access_token}。
 * <p>
 * 优先用 {@link MeetingFeishuMinutesProperties#getUserRefreshToken()} 刷新；
 * 否则使用静态 {@link MeetingFeishuMinutesProperties#getUserAccessToken()}。
 * 飞书轮换 refresh_token 时由 {@link FeishuMinutesRefreshTokenPersister} 自动写回 DB。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuMinutesUserTokenProvider {

    private final MeetingFeishuMinutesProperties minutesProperties;
    private final FeishuMinutesRefreshTokenPersister refreshTokenPersister;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${meeting.feishu.base-url:https://open.feishu.cn}")
    private String feishuBaseUrl;

    @Value("${meeting.feishu.app-id:}")
    private String appId;

    @Value("${meeting.feishu.app-secret:}")
    private String appSecret;

    private volatile String cachedAccessToken;
    private volatile long tokenExpiryMs;
    private volatile String cachedRefreshToken;

    /**
     * @return 可用的 user_access_token；未配置时返回 {@code null}
     */
    public String getUserAccessToken() {
        String refresh = trim(minutesProperties.getUserRefreshToken());
        if (!refresh.isBlank()) {
            return refreshUserAccessToken(refresh);
        }
        String staticToken = trim(minutesProperties.getUserAccessToken());
        return staticToken.isBlank() ? null : staticToken;
    }

    public boolean isConfigured() {
        return !trim(minutesProperties.getUserRefreshToken()).isBlank()
                || !trim(minutesProperties.getUserAccessToken()).isBlank();
    }

    /** Admin 热更或 env 变更后清缓存，强制下次 refresh。 */
    public void invalidateCache() {
        synchronized (this) {
            cachedAccessToken = null;
            tokenExpiryMs = 0;
            cachedRefreshToken = null;
        }
    }

    private String refreshUserAccessToken(String refreshToken) {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryMs
                && refreshToken.equals(cachedRefreshToken)) {
            return cachedAccessToken;
        }
        synchronized (this) {
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryMs
                    && refreshToken.equals(cachedRefreshToken)) {
                return cachedAccessToken;
            }
            try {
                if (appId.isBlank() || appSecret.isBlank()) {
                    log.warn("FeishuMinutes user token refresh skipped: meeting.feishu.app-id/secret not configured");
                    return null;
                }
                String url = feishuBaseUrl + "/open-apis/authen/v2/oauth/token";
                Map<String, String> body = new HashMap<>();
                body.put("grant_type", "refresh_token");
                body.put("client_id", appId);
                body.put("client_secret", appSecret);
                body.put("refresh_token", refreshToken);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                JsonNode root = restTemplate.postForObject(
                        url, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), JsonNode.class);
                if (root == null || root.path("code").asInt(0) != 0) {
                    String err = root == null ? "empty body"
                            : root.path("error_description").asText(root.path("msg").asText("unknown"));
                    log.warn("FeishuMinutes user token refresh failed: code={}, msg={}",
                            root == null ? -1 : root.path("code").asInt(-1), err);
                    return null;
                }
                String accessToken = root.path("access_token").asText("");
                if (accessToken.isBlank()) {
                    log.warn("FeishuMinutes user token refresh empty access_token");
                    return null;
                }
                String newRefresh = root.path("refresh_token").asText("");
                String effectiveRefresh = newRefresh.isBlank() ? refreshToken : newRefresh;
                cachedAccessToken = accessToken;
                cachedRefreshToken = effectiveRefresh;
                int expiresIn = root.path("expires_in").asInt(7200);
                tokenExpiryMs = System.currentTimeMillis() + Math.max(60, expiresIn - 120) * 1000L;
                refreshTokenPersister.persistIfRotated(newRefresh);
                log.info("FeishuMinutes user_access_token refreshed, expiresInSec={}", expiresIn);
                return cachedAccessToken;
            } catch (Exception e) {
                log.warn("FeishuMinutes user token refresh exception: {}", e.getMessage());
                return null;
            }
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
