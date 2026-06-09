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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingServerBridgeService {

    private final RuntimeBridgeProperties bridgeProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean triggerRuntimeReload() {
        try {
            return postInternal("/api/v1/internal/runtime-config/reload", null) != null;
        } catch (Exception e) {
            log.error("meeting-server runtime reload failed: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> refreshHostAgendaEnriched(int presetTypeCode, boolean dryRun, List<String> meetingIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("presetTypeCode", presetTypeCode);
        body.put("dryRun", dryRun);
        if (meetingIds != null && !meetingIds.isEmpty()) {
            body.put("meetingIds", meetingIds);
        }
        JsonNode data = postInternal("/api/v1/internal/meetings/refresh-host-agenda", body);
        if (data == null) {
            throw new BusinessException("meeting-server refresh-host-agenda failed");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", data.path("dryRun").asBoolean());
        result.put("count", data.path("count").asInt());
        result.put("enriched", data.path("enriched").asBoolean(true));
        result.put("note", data.path("note").asText(""));
        if (data.has("meetingIds") && data.get("meetingIds").isArray()) {
            result.put("meetingIds", objectMapper.convertValue(data.get("meetingIds"), List.class));
        }
        return result;
    }

    public void refreshPresetCache(int presetTypeCode) {
        String path = presetTypeCode >= 1 && presetTypeCode <= 5
                ? "/api/v1/internal/presets/" + presetTypeCode + "/refresh-cache"
                : "/api/v1/internal/presets/refresh-cache-all";
        postInternal(path, null);
    }

    public void executePipeline(String meetingId, String stage, String templateCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetingId", meetingId);
        body.put("stage", stage);
        body.put("templateCode", templateCode);
        JsonNode data = postInternal("/api/v1/internal/pipeline/execute", body);
        if (data == null) {
            throw new BusinessException("execute pipeline failed");
        }
    }

    public Map<String, Object> executePipelineByPreset(int presetTypeCode, String stage, String templateCode, boolean skipExisting) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("presetTypeCode", presetTypeCode);
        body.put("stage", stage);
        body.put("templateCode", templateCode);
        body.put("skipExisting", skipExisting);
        JsonNode data = postInternal("/api/v1/internal/pipeline/execute-by-preset", body);
        if (data == null) {
            throw new BusinessException("execute pipeline by preset failed");
        }
        return objectMapper.convertValue(data, Map.class);
    }

    public JsonNode queryInternalVoiceprintFeatures(String groupId) {
        String path = "/api/v1/internal/voiceprint/features";
        if (groupId != null && !groupId.isBlank()) {
            path = path + "?groupId=" + URLEncoder.encode(groupId.trim(), StandardCharsets.UTF_8);
        }
        return getInternal(path);
    }

    public JsonNode queryInternalVoiceprintAllFeaturesByGroupId(String groupId) {
        String path = "/api/v1/internal/voiceprint/features/all";
        if (groupId != null && !groupId.isBlank()) {
            path = path + "?groupId=" + URLEncoder.encode(groupId.trim(), StandardCharsets.UTF_8);
        }
        return getInternal(path);
    }

    public JsonNode deleteInternalVoiceprintFeature(String featureId) {
        return postInternal("/api/v1/internal/voiceprint/features/delete", Map.of("featureId", featureId));
    }

    public JsonNode updateInternalVoiceprintFeature(String groupId, String featureId, String featureInfo, String audioBase64, boolean cover) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("featureId", featureId);
        body.put("featureInfo", featureInfo);
        body.put("audioBase64", audioBase64);
        body.put("cover", cover);
        return postInternal("/api/v1/internal/voiceprint/features/update", body);
    }

    public JsonNode createInternalVoiceprintGroup(String groupId, String groupName, String groupInfo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("groupName", groupName);
        body.put("groupInfo", groupInfo);
        return postInternal("/api/v1/internal/voiceprint/groups/create", body);
    }

    public JsonNode deleteInternalVoiceprintGroup(String groupId) {
        return postInternal("/api/v1/internal/voiceprint/groups/delete", Map.of("groupId", groupId));
    }

    public JsonNode searchInternalVoiceprint1N(String groupId, String audioBase64, Integer topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("audioBase64", audioBase64);
        body.put("topK", topK);
        return postInternal("/api/v1/internal/voiceprint/search/1n", body);
    }

    public JsonNode searchInternalVoiceprint1V1(String groupId, String featureId, String audioBase64) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupId", groupId);
        body.put("featureId", featureId);
        body.put("audioBase64", audioBase64);
        return postInternal("/api/v1/internal/voiceprint/search/1v1", body);
    }

    private JsonNode postInternal(String path, Object body) {
        String base = bridgeProperties.getMeetingServerBaseUrl();
        String token = bridgeProperties.getInternalReloadToken();
        if (base == null || base.isBlank() || token == null || token.isBlank()) {
            throw new BusinessException(502,
                    "meeting-server bridge 未配置：请设置 MEETING_SERVER_URL 与 INTERNAL_RELOAD_TOKEN");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        List<String> candidateUrls = buildCandidateUrls(base, path);
        try {
            HttpEntity<String> entity = body != null
                    ? new HttpEntity<>(objectMapper.writeValueAsString(body), headers)
                    : new HttpEntity<>(headers);
            Exception lastError = null;
            for (String url : candidateUrls) {
                try {
                    log.info("meeting-server internal POST try: path={}, url={}", path, url);
                    ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                    if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                        throw new BusinessException(502,
                                "meeting-server internal 调用失败: POST " + path + " HTTP " + resp.getStatusCode().value());
                    }
                    JsonNode root = objectMapper.readTree(resp.getBody());
                    if (root.path("code").asInt(-1) != 0) {
                        String msg = root.path("message").asText("unknown");
                        throw new BusinessException(502,
                                "meeting-server internal 业务失败: POST " + path + ", msg=" + msg);
                    }
                    return root.path("data");
                } catch (HttpStatusCodeException he) {
                    lastError = he;
                    String bodySnippet = safeSnippet(he.getResponseBodyAsString());
                    log.warn("meeting-server internal POST failed: path={}, url={}, status={}, body={}",
                            path, url, he.getStatusCode().value(), bodySnippet);
                    if (he.getStatusCode().value() == 404) {
                        continue;
                    }
                    throw new BusinessException(502,
                            "meeting-server internal 调用异常: POST " + path + ", url=" + url
                                    + ", status=" + he.getStatusCode().value()
                                    + ", body=" + bodySnippet);
                } catch (Exception e) {
                    lastError = e;
                    log.warn("meeting-server internal POST failed: path={}, url={}, error={}",
                            path, url, safeSnippet(e.getMessage()));
                    throw e;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new BusinessException(502, "meeting-server internal 调用失败: POST " + path);
        } catch (Exception e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            log.warn("internal api {} failed: {}", path, e.getMessage());
            throw new BusinessException(502,
                    "meeting-server internal 调用异常: POST " + path + ", error=" + e.getMessage());
        }
    }

    private JsonNode getInternal(String path) {
        String base = bridgeProperties.getMeetingServerBaseUrl();
        String token = bridgeProperties.getInternalReloadToken();
        if (base == null || base.isBlank() || token == null || token.isBlank()) {
            throw new BusinessException(502,
                    "meeting-server bridge 未配置：请设置 MEETING_SERVER_URL 与 INTERNAL_RELOAD_TOKEN");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        List<String> candidateUrls = buildCandidateUrls(base, path);
        try {
            HttpEntity<String> entity = new HttpEntity<>(headers);
            Exception lastError = null;
            for (String url : candidateUrls) {
                try {
                    log.info("meeting-server internal GET try: path={}, url={}", path, url);
                    ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                    if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                        throw new BusinessException(502,
                                "meeting-server internal 调用失败: GET " + path + " HTTP " + resp.getStatusCode().value());
                    }
                    JsonNode root = objectMapper.readTree(resp.getBody());
                    if (root.path("code").asInt(-1) != 0) {
                        String msg = root.path("message").asText("unknown");
                        throw new BusinessException(502,
                                "meeting-server internal 业务失败: GET " + path + ", msg=" + msg);
                    }
                    return root.path("data");
                } catch (HttpStatusCodeException he) {
                    lastError = he;
                    String bodySnippet = safeSnippet(he.getResponseBodyAsString());
                    log.warn("meeting-server internal GET failed: path={}, url={}, status={}, body={}",
                            path, url, he.getStatusCode().value(), bodySnippet);
                    if (he.getStatusCode().value() == 404) {
                        continue;
                    }
                    throw new BusinessException(502,
                            "meeting-server internal 调用异常: GET " + path + ", url=" + url
                                    + ", status=" + he.getStatusCode().value()
                                    + ", body=" + bodySnippet);
                } catch (Exception e) {
                    lastError = e;
                    log.warn("meeting-server internal GET failed: path={}, url={}, error={}",
                            path, url, safeSnippet(e.getMessage()));
                    throw e;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new BusinessException(502, "meeting-server internal 调用失败: GET " + path);
        } catch (Exception e) {
            if (e instanceof BusinessException be) {
                throw be;
            }
            log.warn("internal api {} failed: {}", path, e.getMessage());
            throw new BusinessException(502,
                    "meeting-server internal 调用异常: GET " + path + ", error=" + e.getMessage());
        }
    }

    private List<String> buildCandidateUrls(String base, String path) {
        String trimmed = base.replaceAll("/$", "");
        List<String> urls = new ArrayList<>();
        urls.add(trimmed + path);
        if (!trimmed.endsWith("/meeting-server")) {
            urls.add(trimmed + "/meeting-server" + path);
        }
        return urls;
    }

    private String safeSnippet(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 400 ? compact.substring(0, 400) + "..." : compact;
    }
}
