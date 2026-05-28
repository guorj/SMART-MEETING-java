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
import org.springframework.web.client.RestTemplate;

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
        return postInternal("/api/v1/internal/runtime-config/reload", null) != null;
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

    private JsonNode postInternal(String path, Object body) {
        String base = bridgeProperties.getMeetingServerBaseUrl();
        String token = bridgeProperties.getInternalReloadToken();
        if (base == null || base.isBlank() || token == null || token.isBlank()) {
            log.warn("meeting-server bridge not configured");
            return null;
        }
        String url = base.replaceAll("/$", "") + path;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        try {
            HttpEntity<String> entity = body != null
                    ? new HttpEntity<>(objectMapper.writeValueAsString(body), headers)
                    : new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.path("code").asInt(-1) != 0) {
                log.warn("internal api {} code={} msg={}", path, root.path("code").asInt(), root.path("message").asText());
                return null;
            }
            return root.path("data");
        } catch (Exception e) {
            log.warn("internal api {} failed: {}", path, e.getMessage());
            return null;
        }
    }
}
