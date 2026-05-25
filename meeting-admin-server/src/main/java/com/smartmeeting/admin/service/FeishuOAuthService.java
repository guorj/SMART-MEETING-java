package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.auth.AdminSessionStore;
import com.smartmeeting.admin.config.FeishuOAuthProperties;
import com.smartmeeting.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private final FeishuOAuthProperties oauthProperties;
    private final AdminSessionStore sessionStore;
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

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
