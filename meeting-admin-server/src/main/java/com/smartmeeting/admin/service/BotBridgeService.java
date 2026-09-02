package com.smartmeeting.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.config.RuntimeBridgeProperties;
import com.smartmeeting.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/** 代理 feishu-scheduled-bot REST API（服务端持有 X-API-Key）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotBridgeService {

    private final RuntimeBridgeProperties bridgeProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return bridgeProperties.getBotBaseUrl() != null && !bridgeProperties.getBotBaseUrl().isBlank()
                && bridgeProperties.getScheduledBotApikey() != null && !bridgeProperties.getScheduledBotApikey().isBlank();
    }

    public Map<String, Object> healthCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", isConfigured());
        result.put("botBaseUrl", bridgeProperties.getBotBaseUrl());
        if (!isConfigured()) {
            result.put("reachable", false);
            result.put("message", "未配置 MEETING_NOTIFY_BOT_URL / SCHEDULED_BOT_APIKEY");
            return result;
        }
        try {
            get("/api/tasks", Map.of("page", "0", "size", "1"));
            result.put("reachable", true);
            result.put("message", "ok");
        } catch (BusinessException e) {
            result.put("reachable", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> reloadSchedule() {
        JsonNode body = post("/api/admin/reload-schedule", null);
        return objectMapper.convertValue(body, Map.class);
    }

    public JsonNode get(String path, Map<String, String> query) {
        return exchange(HttpMethod.GET, path, query, null);
    }

    public JsonNode post(String path, Object body) {
        return exchange(HttpMethod.POST, path, null, body);
    }

    /** POST 请求；bot 返回指定非 2xx 时也解析 JSON 正文（如 /execute 的 409 REJECTED）。 */
    public JsonNode postAllowingStatuses(String path, Object body, int... allowedStatuses) {
        return exchange(HttpMethod.POST, path, null, body, allowedStatuses);
    }

    public JsonNode put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, null, body);
    }

    public JsonNode patch(String path, Object body) {
        return exchange(HttpMethod.PATCH, path, null, body);
    }

    public void delete(String path) {
        exchange(HttpMethod.DELETE, path, null, null);
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, String> query, Object body) {
        return exchange(method, path, query, body, new int[0]);
    }

    private JsonNode exchange(HttpMethod method, String path, Map<String, String> query, Object body,
                              int... allowedStatuses) {
        if (!isConfigured()) {
            throw new BusinessException(502, "feishu-scheduled-bot 未配置：请设置 MEETING_NOTIFY_BOT_URL 与 SCHEDULED_BOT_APIKEY");
        }
        String base = bridgeProperties.getBotBaseUrl().replaceAll("/$", "");
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(base + path);
        if (query != null) {
            query.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    uri.queryParam(k, v);
                }
            });
        }
        String url = uri.toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", bridgeProperties.getScheduledBotApikey());
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            HttpEntity<String> entity = body != null
                    ? new HttpEntity<>(objectMapper.writeValueAsString(body), headers)
                    : new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return parseBody(resp.getBody());
            }
            throw new BusinessException("bot " + method + " " + path + " HTTP " + resp.getStatusCode());
        } catch (HttpStatusCodeException e) {
            if (isAllowedStatus(e.getStatusCode().value(), allowedStatuses)) {
                return parseBody(e.getResponseBodyAsString());
            }
            String msg = e.getResponseBodyAsString();
            log.warn("bot {} {} failed: {} {}", method, path, e.getStatusCode(), msg);
            throw new BusinessException(502,
                    "feishu-scheduled-bot 请求失败: " + method + " " + path
                            + ", HTTP " + e.getStatusCode().value()
                            + ", detail=" + summarizeError(msg));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("bot {} {} failed: {}", method, path, e.getMessage());
            throw new BusinessException(502,
                    "feishu-scheduled-bot 调用异常: " + method + " " + path + ", error=" + e.getMessage());
        }
    }

    private JsonNode parseBody(String body) {
        try {
            if (body == null || body.isBlank()) {
                return objectMapper.nullNode();
            }
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException("bot 响应 JSON 解析失败: " + e.getMessage());
        }
    }

    private static boolean isAllowedStatus(int status, int... allowedStatuses) {
        if (allowedStatuses == null) {
            return false;
        }
        for (int allowed : allowedStatuses) {
            if (allowed == status) {
                return true;
            }
        }
        return false;
    }

    private static String summarizeError(String body) {
        if (body == null || body.isBlank()) {
            return "empty body";
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }
}
