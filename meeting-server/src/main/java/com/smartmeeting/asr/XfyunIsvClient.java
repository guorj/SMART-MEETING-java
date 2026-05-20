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
 * 讯飞 ISV 私有化声纹识别 HTTP 客户端。
 * <p>
 * 封装注册、识别、删除、查询声纹组等 REST 调用，请求体签名由
 * {@link com.smartmeeting.util.XfyunSignatureUtil#generateSignatureForObject} 完成；
 * 与 {@link RestTemplate}、{@link ObjectMapper} 协作，并在进程内维护 userId ↔ featureId 缓存。
 * </p>
 * <p>
 * 官方文档：<a href="https://www.xfyun.cn/doc/isv/isv/API.html">ISV API</a>。
 * 默认声纹组 ID 为配置项 {@code meeting.asr.xfyun.isv-group-id}（如 smart_meeting_vp）。
 * </p>
 *
 * @see VoiceprintMember
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
     * 向声纹组注册新用户的声纹特征（data.status=2）。
     *
     * @param userId    OA 用户唯一标识
     * @param userName  展示用姓名，写入 vcnUserName
     * @param audioData PCM 音频字节，建议 5～10 秒有效语音
     * @return 成功时返回讯飞 featureId；HTTP/业务失败或异常时返回 null
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
     * 从短音频片段识别声纹（data.status=3），返回最匹配 featureId 或降级标识。
     *
     * @param audioData PCM 音频，建议 3～5 秒
     * @return 置信度 &gt; 0.6 时返回 featureId；否则或失败时返回 {@code speaker_unknown}
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
     * 按 featureId 删除声纹特征（data.status=4），并同步清理本地缓存。
     *
     * @param featureId 注册时返回的声纹特征 ID
     * @return 删除成功返回 true；请求或业务错误返回 false
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
     * 查询当前声纹组全部成员（data.status=1）。
     *
     * @return 成员列表；失败时返回空列表（非 null）
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
     * 返回配置中的声纹组 ID。
     *
     * @return groupId，如 smart_meeting_vp
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * 根据 featureId 从本地缓存解析 userId。
     *
     * @param featureId 声纹特征 ID
     * @return 对应 userId；未缓存时返回 null
     */
    public String getUserIdByFeatureId(String featureId) {
        return featureIdToUserId.get(featureId);
    }

    /**
     * 根据 userId 从本地缓存解析 featureId。
     *
     * @param userId OA 用户 ID
     * @return 对应 featureId；未缓存时返回 null
     */
    public String getFeatureIdByUserId(String userId) {
        return userIdToFeatureId.get(userId);
    }

    /**
     * 调用 {@link #listVoiceprintGroup()} 全量拉取并重建 userId ↔ featureId 双向缓存。
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

    /**
     * 声纹组成员快照，对应查询接口返回的 vcnGroupMembers 元素。
     */
    public static class VoiceprintMember {

        /** 讯飞声纹特征 ID */
        public String featureId;

        /** 注册时填写的用户姓名（vcnUserName） */
        public String userName;

        /** 业务用户 ID（vcnUserId） */
        public String userId;

        /** 注册时间字符串（createTime，格式由讯飞返回） */
        public String registeredAt;
    }
}