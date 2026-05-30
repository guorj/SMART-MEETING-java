package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.internal.InternalVoiceprintAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/voiceprint")
@RequiredArgsConstructor
public class InternalVoiceprintAdminController {

    private final InternalApiAuth internalApiAuth;
    private final InternalVoiceprintAdminService internalVoiceprintAdminService;

    @GetMapping("/features")
    public ApiResponse<List<InternalVoiceprintAdminService.FeatureView>> queryFeatures(
            @RequestParam(required = false) String groupId,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        return ApiResponse.ok(internalVoiceprintAdminService.queryFeatureList(groupId));
    }

    @GetMapping("/features/all")
    public ApiResponse<List<InternalVoiceprintAdminService.FeatureView>> queryAllLocalFeaturesByGroupId(
            @RequestParam(required = false) String groupId,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        return ApiResponse.ok(internalVoiceprintAdminService.queryAllLocalFeaturesByGroupId(groupId));
    }

    @PostMapping("/features/delete")
    public ApiResponse<Map<String, Object>> deleteFeature(
            @RequestBody FeatureDeleteRequest body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        boolean ok = internalVoiceprintAdminService.deleteFeature(body.getFeatureId());
        return ApiResponse.ok(Map.of("success", ok));
    }

    @PostMapping("/groups/create")
    public ApiResponse<Map<String, Object>> createGroup(
            @RequestBody GroupCreateRequest body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        boolean ok = internalVoiceprintAdminService.createGroup(body.getGroupId(), body.getGroupName(), body.getGroupInfo());
        return ApiResponse.ok(Map.of("success", ok));
    }

    @PostMapping("/groups/delete")
    public ApiResponse<Map<String, Object>> deleteGroup(
            @RequestBody GroupDeleteRequest body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        boolean ok = internalVoiceprintAdminService.deleteGroup(body.getGroupId());
        return ApiResponse.ok(Map.of("success", ok));
    }

    @PostMapping("/features/update")
    public ApiResponse<Map<String, Object>> updateFeature(
            @RequestBody FeatureUpdateRequest body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        byte[] audioData = decodeAudioBase64(body.getAudioBase64(), "更新特征");
        boolean ok = internalVoiceprintAdminService.updateFeature(
                body.getGroupId(),
                body.getFeatureId(),
                body.getFeatureInfo(),
                audioData,
                body.isCover()
        );
        return ApiResponse.ok(Map.of("success", ok));
    }

    @PostMapping("/search/1n")
    public ApiResponse<List<InternalVoiceprintAdminService.ScoreView>> search1N(
            @RequestBody Search1NRequest body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        byte[] audioData = decodeAudioBase64(body.getAudioBase64(), "1:N 检索");
        int topK = body.getTopK() == null ? 3 : body.getTopK();
        return ApiResponse.ok(internalVoiceprintAdminService.search1N(body.getGroupId(), audioData, topK));
    }

    @PostMapping("/search/1v1")
    public ApiResponse<InternalVoiceprintAdminService.ScoreView> search1V1(
            @RequestBody Search1V1Request body,
            HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        byte[] audioData = decodeAudioBase64(body.getAudioBase64(), "1:1 比对");
        return ApiResponse.ok(internalVoiceprintAdminService.search1V1(body.getGroupId(), body.getFeatureId(), audioData));
    }

    private byte[] decodeAudioBase64(String audioBase64, String action) {
        if (audioBase64 == null || audioBase64.isBlank()) {
            throw new BusinessException(400, action + "失败：audioBase64 不能为空");
        }
        try {
            return Base64.getDecoder().decode(audioBase64);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, action + "失败：audioBase64 非法");
        }
    }

    public static class FeatureDeleteRequest {
        private String featureId;
        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
    }

    public static class GroupCreateRequest {
        private String groupId;
        private String groupName;
        private String groupInfo;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
        public String getGroupInfo() { return groupInfo; }
        public void setGroupInfo(String groupInfo) { this.groupInfo = groupInfo; }
    }

    public static class GroupDeleteRequest {
        private String groupId;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
    }

    public static class FeatureUpdateRequest {
        private String groupId;
        private String featureId;
        private String featureInfo;
        private String audioBase64;
        private boolean cover = true;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public String getFeatureInfo() { return featureInfo; }
        public void setFeatureInfo(String featureInfo) { this.featureInfo = featureInfo; }
        public String getAudioBase64() { return audioBase64; }
        public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
        public boolean isCover() { return cover; }
        public void setCover(boolean cover) { this.cover = cover; }
    }

    public static class Search1NRequest {
        private String groupId;
        private String audioBase64;
        private Integer topK;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getAudioBase64() { return audioBase64; }
        public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
        public Integer getTopK() { return topK; }
        public void setTopK(Integer topK) { this.topK = topK; }
    }

    public static class Search1V1Request {
        private String groupId;
        private String featureId;
        private String audioBase64;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public String getAudioBase64() { return audioBase64; }
        public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
    }
}

