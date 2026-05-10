package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.util.XfyunSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 讯飞 ISV 声纹识别客户端
 *
 * API 文档: https://www.xfyun.cn/doc/isv/isv/API.html
 *
 * 功能:
 * - registerVoiceprint: 注册声纹特征
 * - identifyVoiceprint: 识别声纹（返回说话人）
 * - deleteVoiceprint: 删除声纹
 * - listVoiceprint: 查询声纹组成员
 *
 * 声纹组:
 * - group_id: smart_meeting_vp（配置项）
 * - 每个用户注册一个 feature_id，有效期通常90天
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XfyunIsvClient {

    @Value("${meeting.asr.xfyun.app-id:}")
    private String appId;

    @Value("${meeting.asr.xfyun.api-key:}")
    private String apiKey;

    @Value("${meeting.asr.xfyun.api-secret:}")
    private String apiSecret;

    @Value("${meeting.asr.xfyun.isv-url:https://api.xf-yun.com/v1/private/s1aa729d0}")
    private String isvApiUrl;

    @Value("${meeting.asr.xfyun.isv-group-id:smart_meeting_vp}")
    private String groupId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 声纹缓存（本地缓存 feature_id）
    private final Map<String, String> userIdToFeatureId = new HashMap<>();
    private final Map<String, String> featureIdToUserId = new HashMap<>();

    /**
     * 注册声纹
     *
     * @param userId 用户标识（OA userId）
     * @param userName 用户姓名
     * @param audioData 音频数据（PCM格式，建议5-10秒）
     * @return featureId 声纹特征ID，失败返回null
     */
    public String registerVoiceprint(String userId, String userName, byte[] audioData) {
        log.info("Registering voiceprint: userId={}, userName={}, audioLen={}bytes", userId, userName, audioData.length);

        try {
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> business = new HashMap<>();
            business.put("groupId", groupId);
            business.put("vcnUserName", userName);
            business.put("vcnUserId", userId);

            Map<String, Object> data = new HashMap<>();
            data.put("status", 2); // 2=注册
            data.put("audio", Base64.getEncoder().encodeToString(audioData));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("common", Map.of("app_id", appId));
            body.put("business", business);
            body.put("data", data);

            // 添加签名
            String signature = XfyunSignatureUtil.generateSignatureForObject(apiKey, apiSecret, body);
            body.put("signature", signature);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(isvApiUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Register voiceprint request failed: {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt();
            if (code != 0) {
                log.error("Register voiceprint error: code={}, desc={}", code, root.path("desc").asText());
                return null;
            }

            String featureId = root.path("data").path("featureId").asText();
            log.info("Voiceprint registered: userId={}, featureId={}", userId, featureId);

            // 缓存
            userIdToFeatureId.put(userId, featureId);
            featureIdToUserId.put(featureId, userId);

            return featureId;

        } catch (Exception e) {
            log.error("Register voiceprint failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 识别声纹 - 从音频片段识别说话人
     *
     * @param audioData 音频数据（PCM格式，建议3-5秒）
     * @return 识别结果：featureId（或 speaker_N 降级标识）
     */
    public String identifyVoiceprint(byte[] audioData) {
        log.debug("Identifying voiceprint from audio: {} bytes", audioData.length);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> business = new HashMap<>();
            business.put("groupId", groupId);
            business.put("vcnTopN", 1); // 返回最匹配的1个

            Map<String, Object> data = new HashMap<>();
            data.put("status", 3); // 3=识别
            data.put("audio", Base64.getEncoder().encodeToString(audioData));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("common", Map.of("app_id", appId));
            body.put("business", business);
            body.put("data", data);

            String signature = XfyunSignatureUtil.generateSignatureForObject(apiKey, apiSecret, body);
            body.put("signature", signature);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(isvApiUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Identify voiceprint request failed: {}", response.getStatusCode());
                return "speaker_unknown";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt();
            if (code != 0) {
                log.warn("Identify voiceprint error: code={}, desc={}", code, root.path("desc").asText());
                return "speaker_unknown";
            }

            // 解析结果：返回 featureId 和置信度
            JsonNode results = root.path("data").path("vcnResults");
            if (results.isArray() && results.size() > 0) {
                JsonNode topResult = results.get(0);
                String featureId = topResult.path("featureId").asText();
                double confidence = topResult.path("confidence").asDouble(0.0);

                log.debug("Identified: featureId={}, confidence={}", featureId, confidence);

                if (confidence > 0.6) {
                    // 高置信度，返回 featureId
                    return featureId;
                }
            }

            return "speaker_unknown";

        } catch (Exception e) {
            log.error("Identify voiceprint failed: {}", e.getMessage());
            return "speaker_unknown";
        }
    }

    /**
     * 删除声纹
     *
     * @param featureId 声纹特征ID
     * @return 是否成功
     */
    public boolean deleteVoiceprint(String featureId) {
        log.info("Deleting voiceprint: featureId={}", featureId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> business = new HashMap<>();
            business.put("groupId", groupId);
            business.put("featureId", featureId);

            Map<String, Object> data = new HashMap<>();
            data.put("status", 4); // 4=删除

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("common", Map.of("app_id", appId));
            body.put("business", business);
            body.put("data", data);

            String signature = XfyunSignatureUtil.generateSignatureForObject(apiKey, apiSecret, body);
            body.put("signature", signature);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(isvApiUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Delete voiceprint request failed: {}", response.getStatusCode());
                return false;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt();
            if (code != 0) {
                log.error("Delete voiceprint error: code={}, desc={}", code, root.path("desc").asText());
                return false;
            }

            // 清理缓存
            String userId = featureIdToUserId.remove(featureId);
            if (userId != null) {
                userIdToFeatureId.remove(userId);
            }

            log.info("Voiceprint deleted: featureId={}", featureId);
            return true;

        } catch (Exception e) {
            log.error("Delete voiceprint failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查询声纹组成员
     *
     * @return 成员列表（featureId + userName）
     */
    public List<VoiceprintMember> listVoiceprintGroup() {
        log.info("Listing voiceprint group: groupId={}", groupId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> business = new HashMap<>();
            business.put("groupId", groupId);

            Map<String, Object> data = new HashMap<>();
            data.put("status", 1); // 1=查询组信息

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("common", Map.of("app_id", appId));
            body.put("business", business);
            body.put("data", data);

            String signature = XfyunSignatureUtil.generateSignatureForObject(apiKey, apiSecret, body);
            body.put("signature", signature);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(isvApiUrl, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("List voiceprint request failed: {}", response.getStatusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt();
            if (code != 0) {
                log.error("List voiceprint error: code={}, desc={}", code, root.path("desc").asText());
                return List.of();
            }

            JsonNode members = root.path("data").path("vcnGroupMembers");
            List<VoiceprintMember> result = new ArrayList<>();
            if (members.isArray()) {
                for (JsonNode member : members) {
                    VoiceprintMember vm = new VoiceprintMember();
                    vm.featureId = member.path("featureId").asText();
                    vm.userName = member.path("vcnUserName").asText();
                    vm.userId = member.path("vcnUserId").asText();
                    vm.registeredAt = member.path("createTime").asText();
                    result.add(vm);
                }
            }

            log.info("Voiceprint group has {} members", result.size());
            return result;

        } catch (Exception e) {
            log.error("List voiceprint failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取声纹组ID
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * 根据 featureId 获取 userId（从缓存）
     */
    public String getUserIdByFeatureId(String featureId) {
        return featureIdToUserId.get(featureId);
    }

    /**
     * 根据 userId 获取 featureId（从缓存）
     */
    public String getFeatureIdByUserId(String userId) {
        return userIdToFeatureId.get(userId);
    }

    /**
     * 刷新缓存（从服务器同步）
     */
    public void refreshCache() {
        List<VoiceprintMember> members = listVoiceprintGroup();
        userIdToFeatureId.clear();
        featureIdToUserId.clear();

        for (VoiceprintMember member : members) {
            userIdToFeatureId.put(member.userId, member.featureId);
            featureIdToUserId.put(member.featureId, member.userId);
        }

        log.info("Voiceprint cache refreshed: {} entries", userIdToFeatureId.size());
    }

    // 内部类
    public static class VoiceprintMember {
        public String featureId;
        public String userName;
        public String userId;
        public String registeredAt;
    }
}