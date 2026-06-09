package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingIsvProperties;
import com.smartmeeting.config.system.ConfigValueClamp;
import com.smartmeeting.util.XfyunSignatureUtil;
import com.smartmeeting.util.XfyunSignatureUtil.IsvAuthContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 讯飞 ISV 私有化声纹识别 HTTP 客户端。
 * <p>
 * 封装注册、识别、删除、查询声纹组等 REST 调用，请求签名与请求体结构对齐讯飞官方示例；
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
public class XfyunIsvClient {

    /**
     * ISV URL 查询参数已手动百分号编码；须禁用 RestTemplate 二次编码（否则 date 中空格失效 → 403）。
     */
    private static final RestTemplate ISV_HTTP = createIsvRestTemplate();

    private static RestTemplate createIsvRestTemplate() {
        RestTemplate rt = new RestTemplate();
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        rt.setUriTemplateHandler(factory);
        return rt;
    }

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

    private final ObjectMapper objectMapper;

    public XfyunIsvClient(ObjectMapper objectMapper, MeetingIsvProperties isvProperties) {
        this.objectMapper = objectMapper;
        this.isvProperties = isvProperties;
    }

    private final MeetingIsvProperties isvProperties;

    // 声纹缓存（本地缓存 feature_id）
    private final Map<String, String> userIdToFeatureId = new HashMap<>();
    private final Map<String, String> featureIdToUserId = new HashMap<>();

    /**
     * 向声纹组注册新用户的声纹特征（data.status=2）。
     *
     * @param userId    OA 用户唯一标识
     * @param userName  展示用姓名，写入 vcnUserName
     * @param audioData 音频字节（按官方示例为 16k/16bit/mono 的 MP3）
     * @return 成功时返回讯飞 featureId；HTTP/业务失败或异常时返回 null
     */
    public String registerVoiceprint(String userId, String userName, byte[] audioData) {
        log.info("Registering voiceprint: userId={}, userName={}, audioLen={}bytes", userId, userName, audioData.length);

        try {
            String featureId = "vp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("groupId", groupId);
            param.put("featureId", featureId);
            param.put("featureInfo", buildFeatureInfo(userId, userName));
            JsonNode textJson = invokeFuncApi("createFeature", "createFeatureRes", param, audioData);
            if (textJson == null) {
                return null;
            }
            String createdFeatureId = textJson.path("featureId").asText(featureId);
            if (createdFeatureId.isBlank()) {
                createdFeatureId = featureId;
            }
            log.info("Voiceprint registered: userId={}, featureId={}", userId, createdFeatureId);

            // 缓存
            userIdToFeatureId.put(userId, createdFeatureId);
            featureIdToUserId.put(createdFeatureId, userId);

            return createdFeatureId;

        } catch (Exception e) {
            log.error("Register voiceprint failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * ISV 1:N 识别结果。
     */
    public record IdentifyResult(String featureId, double score) {
    }

    private double matchScoreThreshold() {
        return isvProperties != null ? isvProperties.getMatchScoreThreshold() : 0.6;
    }

    /**
     * 从短音频片段识别声纹（data.status=3），返回最匹配 featureId 或降级标识。
     */
    public String identifyVoiceprint(byte[] audioData) {
        log.debug("Identifying voiceprint from audio: {} bytes", audioData.length);
        Optional<IdentifyResult> result = identifyAmongCandidates(audioData, null, 1);
        return result.map(IdentifyResult::featureId).orElse("speaker_unknown");
    }

    /**
     * 全库 1:N 识别，返回最高分且超过阈值的 feature。
     *
     * @param candidateFeatureIds 已废弃，保留参数仅为兼容；不再作参会人白名单过滤
     */
    public Optional<IdentifyResult> identifyAmongCandidates(byte[] audioData,
                                                            Collection<String> candidateFeatureIds,
                                                            int topK) {
        if (audioData == null || audioData.length == 0) {
            return Optional.empty();
        }
        try {
            int effectiveTopK = ConfigValueClamp.effectiveTopK(
                    topK, isvProperties.getSearchTopKMin(), isvProperties.getSearchTopKMax());
            List<SearchScoreItem> scores = search1N(groupId, audioData, effectiveTopK);
            if (scores.isEmpty()) {
                log.debug("ISV 1:N empty scoreList, topK={}", effectiveTopK);
                return Optional.empty();
            }

            double threshold = matchScoreThreshold();
            IdentifyResult best = null;
            for (SearchScoreItem item : scores) {
                if (item.featureId == null || item.featureId.isBlank()) {
                    continue;
                }
                if (item.score > threshold && (best == null || item.score > best.score())) {
                    best = new IdentifyResult(item.featureId, item.score);
                }
            }
            if (best != null) {
                log.debug("ISV 1:N match: featureId={}, score={}, threshold={}, topK={}",
                        best.featureId(), best.score(), threshold, effectiveTopK);
            } else {
                log.debug("ISV 1:N no score above threshold={}, topK={}, resultCount={}",
                        threshold, effectiveTopK, scores.size());
            }
            return Optional.ofNullable(best);
        } catch (Exception e) {
            log.error("Identify voiceprint failed: {}", e.getMessage());
            return Optional.empty();
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
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("groupId", groupId);
            param.put("featureId", featureId);
            JsonNode textJson = invokeFuncApi("deleteFeature", "deleteFeatureRes", param, null);
            if (textJson == null || !"success".equalsIgnoreCase(textJson.path("msg").asText(""))) {
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
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("groupId", groupId);
            JsonNode members = invokeFuncApi("queryFeatureList", "queryFeatureListRes", param, null);
            List<VoiceprintMember> result = new ArrayList<>();
            if (members.isArray()) {
                for (JsonNode member : members) {
                    VoiceprintMember vm = new VoiceprintMember();
                    vm.featureId = member.path("featureId").asText();
                    String featureInfo = member.path("featureInfo").asText("");
                    vm.userId = parseFeatureInfoPart(featureInfo, "userId");
                    vm.userName = parseFeatureInfoPart(featureInfo, "userName");
                    vm.registeredAt = parseFeatureInfoPart(featureInfo, "ts");
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

    private String buildFeatureInfo(String userId, String userName) {
        return "userId=" + (userId == null ? "" : userId) + ";userName=" + (userName == null ? "" : userName) + ";ts=" + System.currentTimeMillis();
    }

    private String parseFeatureInfoPart(String featureInfo, String key) {
        if (featureInfo == null || featureInfo.isBlank()) {
            return "";
        }
        String prefix = key + "=";
        for (String p : featureInfo.split(";")) {
            String t = p.trim();
            if (t.startsWith(prefix)) {
                return t.substring(prefix.length());
            }
        }
        return "";
    }

    /**
     * 按文档 queryFeatureList 查询指定声纹库特征列表（推荐用于运维对账）。
     */
    public List<FeatureItem> queryFeatureList(String targetGroupId) {
        String gid = (targetGroupId == null || targetGroupId.isBlank()) ? groupId : targetGroupId.trim();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", gid);
        JsonNode textJson = invokeFuncApi("queryFeatureList", "queryFeatureListRes", param, null);
        if (textJson == null || !textJson.isArray()) {
            return List.of();
        }
        List<FeatureItem> items = new ArrayList<>();
        for (JsonNode n : textJson) {
            FeatureItem it = new FeatureItem();
            it.featureId = n.path("featureId").asText("");
            it.featureInfo = n.path("featureInfo").asText("");
            items.add(it);
        }
        return items;
    }

    /**
     * 创建声纹特征库（group）。
     */
    public boolean createGroup(String targetGroupId, String groupName, String groupInfo) {
        if (targetGroupId == null || targetGroupId.isBlank()) {
            return false;
        }
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", targetGroupId.trim());
        if (groupName != null) {
            param.put("groupName", groupName);
        }
        if (groupInfo != null) {
            param.put("groupInfo", groupInfo);
        }
        JsonNode textJson = invokeFuncApi("createGroup", "createGroupRes", param, null);
        return textJson != null && targetGroupId.trim().equals(textJson.path("groupId").asText(""));
    }

    /**
     * 删除声纹特征库（group）。
     */
    public boolean deleteGroup(String targetGroupId) {
        if (targetGroupId == null || targetGroupId.isBlank()) {
            return false;
        }
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", targetGroupId.trim());
        JsonNode textJson = invokeFuncApi("deleteGroup", "deleteGroupRes", param, null);
        return textJson != null && "success".equalsIgnoreCase(textJson.path("msg").asText(""));
    }

    /**
     * 按文档 updateFeature 更新声纹特征。
     */
    public boolean updateFeature(String targetGroupId, String featureId, String featureInfo, byte[] audioData, boolean cover) {
        if (featureId == null || featureId.isBlank() || audioData == null || audioData.length == 0) {
            return false;
        }
        String gid = (targetGroupId == null || targetGroupId.isBlank()) ? groupId : targetGroupId.trim();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", gid);
        param.put("featureId", featureId.trim());
        if (featureInfo != null) {
            param.put("featureInfo", featureInfo);
        }
        param.put("cover", cover);
        JsonNode textJson = invokeFuncApi("updateFeature", "updateFeatureRes", param, audioData);
        return textJson != null && "success".equalsIgnoreCase(textJson.path("msg").asText(""));
    }

    /**
     * 按文档 searchFea（1:N）检索。
     */
    public List<SearchScoreItem> search1N(String targetGroupId, byte[] audioData, int topK) {
        if (audioData == null || audioData.length == 0) {
            return List.of();
        }
        String gid = (targetGroupId == null || targetGroupId.isBlank()) ? groupId : targetGroupId.trim();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", gid);
        int topKCap = isvProperties != null ? isvProperties.getSearchTopKMax() : 10;
        param.put("topK", ConfigValueClamp.effectiveTopK(topK, 1, topKCap));
        JsonNode textJson = invokeFuncApi("searchFea", "searchFeaRes", param, audioData);
        if (textJson == null || !textJson.has("scoreList")) {
            return List.of();
        }
        List<SearchScoreItem> out = new ArrayList<>();
        for (JsonNode n : textJson.path("scoreList")) {
            SearchScoreItem item = new SearchScoreItem();
            item.featureId = n.path("featureId").asText("");
            item.featureInfo = n.path("featureInfo").asText("");
            item.score = n.path("score").asDouble(0.0);
            out.add(item);
        }
        return out;
    }

    /**
     * 按文档 searchScoreFea（1:1）比对。
     */
    public SearchScoreItem search1V1(String targetGroupId, String dstFeatureId, byte[] audioData) {
        if (dstFeatureId == null || dstFeatureId.isBlank() || audioData == null || audioData.length == 0) {
            return null;
        }
        String gid = (targetGroupId == null || targetGroupId.isBlank()) ? groupId : targetGroupId.trim();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("groupId", gid);
        param.put("dstFeatureId", dstFeatureId.trim());
        JsonNode textJson = invokeFuncApi("searchScoreFea", "searchScoreFeaRes", param, audioData);
        if (textJson == null) {
            return null;
        }
        SearchScoreItem item = new SearchScoreItem();
        item.featureId = textJson.path("featureId").asText("");
        item.featureInfo = textJson.path("featureInfo").asText("");
        item.score = textJson.path("score").asDouble(0.0);
        return item;
    }

    private JsonNode invokeFuncApi(String func, String responseBlockKey, Map<String, Object> params, byte[] audioData) {
        try {
            String appIdVal = appId == null ? "" : appId.trim();
            String apiKeyVal = apiKey == null ? "" : apiKey.trim();
            String apiSecretVal = apiSecret == null ? "" : apiSecret.trim();
            String isvUrlVal = isvApiUrl == null ? "" : isvApiUrl.trim();

            if (appIdVal.isBlank() || apiKeyVal.isBlank() || apiSecretVal.isBlank()) {
                log.warn("ISV {} invoke skipped: missing appId/apiKey/apiSecret", func);
                return null;
            }

            URI endpoint = URI.create(isvUrlVal);
            String host = endpoint.getHost();
            String requestPath = endpoint.getRawPath();
            if (host == null || host.isBlank()) {
                log.warn("ISV {} invoke skipped: invalid isvApiUrl host={}", func, isvUrlVal);
                return null;
            }
            if (requestPath == null || requestPath.isBlank()) {
                requestPath = "/";
            }
            String serviceId = resolveServiceId(requestPath);
            log.info("ISV {} config fingerprint: appIdSuffix={}, apiKeyPrefix={}, apiSecretLen={}, url={}",
                    func,
                    tail(appIdVal, 4),
                    head(apiKeyVal, 6),
                    apiSecretVal.length(),
                    isvUrlVal);
            IsvAuthContext auth = XfyunSignatureUtil.buildIsvPostAuthUrl(isvUrlVal, apiKeyVal, apiSecretVal);
            URI signedUri = auth.signedUri();
            log.info("ISV {} signed request target: {}{}, serviceId={}, date={}",
                    func, host, requestPath, serviceId, auth.date());

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("app_id", appIdVal);
            header.put("status", 3);

            Map<String, Object> resultSpec = Map.of(
                    "encoding", "utf8",
                    "compress", "raw",
                    "format", "json"
            );
            Map<String, Object> funcParam = new LinkedHashMap<>();
            funcParam.put("func", func);
            if (params != null) {
                funcParam.putAll(params);
            }
            funcParam.put(responseBlockKey, resultSpec);
            Map<String, Object> parameter = Map.of(serviceId, funcParam);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("header", header);
            body.put("parameter", parameter);
            if (audioData != null) {
                Map<String, Object> resource = new LinkedHashMap<>();
                // 当前注册页上传已转换为 16k/16bit/mono PCM，按 raw 传输。
                resource.put("encoding", "raw");
                resource.put("sample_rate", 16000);
                resource.put("channels", 1);
                resource.put("bit_depth", 16);
                resource.put("status", 3);
                resource.put("audio", Base64.getEncoder().encodeToString(audioData));
                body.put("payload", Map.of("resource", resource));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Date", auth.date());
            headers.set("appid", appIdVal);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = ISV_HTTP.postForEntity(signedUri, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("ISV {} http failed: status={}", func, response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("header").path("code").asInt(-1);
            if (code != 0) {
                log.warn("ISV {} business failed: code={}, message={}",
                        func, code, root.path("header").path("message").asText(""));
                return null;
            }
            String textBase64 = root.path("payload").path(responseBlockKey).path("text").asText("");
            if (textBase64.isBlank()) {
                return null;
            }
            String decoded = new String(Base64.getDecoder().decode(textBase64), StandardCharsets.UTF_8);
            if (decoded.startsWith("{") || decoded.startsWith("[")) {
                return objectMapper.readTree(decoded);
            }
            return objectMapper.readTree("{\"msg\":\"" + decoded.replace("\"", "\\\"") + "\"}");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("ISV {} invoke failed: status={}, body={}", func, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.warn("ISV {} invoke failed: {}", func, e.getMessage());
            return null;
        }
    }

    private String resolveServiceId(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "s1aa729d0";
        }
        String p = requestPath.endsWith("/") ? requestPath.substring(0, requestPath.length() - 1) : requestPath;
        int idx = p.lastIndexOf('/');
        String serviceId = idx >= 0 ? p.substring(idx + 1) : p;
        if (serviceId == null || serviceId.isBlank()) {
            return "s1aa729d0";
        }
        return serviceId;
    }

    private String head(String value, int size) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int n = Math.max(0, Math.min(size, value.length()));
        return value.substring(0, n);
    }

    private String tail(String value, int size) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int n = Math.max(0, Math.min(size, value.length()));
        return value.substring(value.length() - n);
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

    public static class FeatureItem {
        public String featureId;
        public String featureInfo;
    }

    public static class SearchScoreItem {
        public String featureId;
        public String featureInfo;
        public double score;
    }
}