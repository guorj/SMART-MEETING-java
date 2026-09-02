package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.auth.AdminSessionStore;
import com.smartmeeting.admin.config.FeishuOAuthProperties;
import com.smartmeeting.admin.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuOAuthService {

    private static final String AUTH_BASE = "https://open.feishu.cn/open-apis/authen/v1";
    private static final String OAUTH_V2_TOKEN = "https://open.feishu.cn/open-apis/authen/v2/oauth/token";

    private static final String MINUTES_REFRESH_TOKEN_KEY = "meeting.feishu.minutes.user-refresh-token";

    private final FeishuOAuthProperties oauthProperties;
    private final AdminSessionStore sessionStore;
    private final SystemConfigAdminService systemConfigAdminService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public String buildAuthorizeUrl() {
        if (!oauthProperties.isEnabled()) {
            throw new BusinessException("feishu oauth disabled");
        }
        String state = UUID.randomUUID().toString();
        return AUTH_BASE + "/authorize"
                + "?app_id=" + url(oauthProperties.getAppId())
                + "&redirect_uri=" + url(oauthProperties.getRedirectUri())
                + "&state=" + url(state);
    }

    public String handleCallback(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("missing code");
        }
        JsonNode tokenBody = exchangeCode(code);
        persistMinutesRefreshToken(tokenBody);
        String userId = tokenBody.path("data").path("user_id").asText(null);
        if (userId == null || userId.isBlank()) {
            userId = tokenBody.path("data").path("open_id").asText(null);
        }
        if (userId == null || userId.isBlank()) {
            throw new BusinessException("feishu user_id not found in token response");
        }
        if (!oauthProperties.getAllowedUserIds().isEmpty()
                && !oauthProperties.getAllowedUserIds().contains(userId)) {
            throw new BusinessException("user not allowed: " + userId);
        }
        return sessionStore.createSession(userId, oauthProperties.getSessionTtlHours());
    }

    /**
     * 妙记 File B 专用 OAuth 回调：v2 换 token、持久化 refresh_token、热更 meeting-server。
     * 授权链接须带 {@code state=minutes-oauth*} 与 {@code offline_access} scope。
     *
     * @param code     授权码
     * @param response 写入简单成功/失败页
     */
    public void handleMinutesOAuthCallback(String code, HttpServletResponse response) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        if (code == null || code.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("<html><body><h3>缺少 code</h3></body></html>");
            return;
        }
        try {
            JsonNode tokenBody = exchangeCodeV2(code, oauthProperties.getRedirectUri());
            String refreshToken = tokenBody.path("refresh_token").asText("");
            if (refreshToken.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("<html><body><h3>未返回 refresh_token</h3>"
                        + "<p>请确认授权 scope 含 offline_access 且应用已开通该权限。</p></body></html>");
                return;
            }
            persistMinutesRefreshToken(refreshToken);
            response.getWriter().write("<html><body><h3>妙记 OAuth 配置成功</h3>"
                    + "<p>refresh_token 已写入系统参数并热更 meeting-server，可关闭本页。</p></body></html>");
        } catch (BusinessException e) {
            log.warn("minutes oauth callback failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("<html><body><h3>授权失败</h3><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    private JsonNode exchangeCodeV2(String code, String redirectUri) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "authorization_code");
            body.put("client_id", oauthProperties.getAppId());
            body.put("client_secret", oauthProperties.getAppSecret());
            body.put("code", code);
            body.put("redirect_uri", redirectUri);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(
                    OAUTH_V2_TOKEN,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                    String.class);
            JsonNode root = objectMapper.readTree(resp);
            if (root.path("code").asInt(0) != 0) {
                String msg = root.path("error_description").asText(root.path("msg").asText("unknown"));
                throw new BusinessException("feishu v2 token error: " + msg);
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("feishu v2 oauth exchange failed: {}", e.getMessage());
            throw new BusinessException("feishu v2 oauth failed: " + e.getMessage());
        }
    }

    private JsonNode exchangeCode(String code) {
        try {
            String url = AUTH_BASE + "/access_token";
            Map<String, String> body = new HashMap<>();
            body.put("grant_type", "authorization_code");
            body.put("code", code);
            body.put("app_id", oauthProperties.getAppId());
            body.put("app_secret", oauthProperties.getAppSecret());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String resp = restTemplate.postForObject(url, new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
            JsonNode root = objectMapper.readTree(resp);
            if (root.path("code").asInt(0) != 0) {
                throw new BusinessException("feishu token error: " + root.path("msg").asText());
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("feishu oauth exchange failed: {}", e.getMessage());
            throw new BusinessException("feishu oauth failed: " + e.getMessage());
        }
    }

    /**
     * OAuth 回调若返回 refresh_token，写入妙记 user-refresh-token 并热更 meeting-server。
     */
    private void persistMinutesRefreshToken(JsonNode tokenBody) {
        String refreshToken = tokenBody.path("data").path("refresh_token").asText("");
        if (refreshToken.isBlank()) {
            refreshToken = tokenBody.path("refresh_token").asText("");
        }
        if (refreshToken.isBlank()) {
            log.info("feishu oauth callback: no refresh_token in response, skip minutes token persist");
            return;
        }
        persistMinutesRefreshToken(refreshToken);
    }

    private void persistMinutesRefreshToken(String refreshToken) {
        try {
            String valueJson = objectMapper.writeValueAsString(refreshToken);
            systemConfigAdminService.upsertInternal(MINUTES_REFRESH_TOKEN_KEY, valueJson, "feishu-oauth");
            boolean reloaded = systemConfigAdminService.triggerRuntimeReload();
            log.info("feishu oauth saved meeting.feishu.minutes.user-refresh-token, runtimeReload={}", reloaded);
        } catch (Exception e) {
            log.warn("feishu oauth failed to persist minutes refresh_token: {}", e.getMessage());
            throw new BusinessException("persist refresh_token failed: " + e.getMessage());
        }
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
