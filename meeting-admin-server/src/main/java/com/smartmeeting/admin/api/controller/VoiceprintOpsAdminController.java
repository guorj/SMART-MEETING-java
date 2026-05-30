package com.smartmeeting.admin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.exception.BusinessException;
import com.smartmeeting.admin.service.MeetingServerBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/voiceprints")
@RequiredArgsConstructor
public class VoiceprintOpsAdminController {

    private final MeetingServerBridgeService meetingServerBridgeService;

    /** P0: 查询讯飞特征库列表（带本地映射补充）。 */
    @GetMapping("/features")
    public ApiResponse<JsonNode> features(@RequestParam(required = false) String groupId) {
        JsonNode data = meetingServerBridgeService.queryInternalVoiceprintFeatures(groupId);
        return ApiResponse.ok(requireData(data, "查询声纹特征失败：meeting-server 返回空结果"));
    }

    /** P0+: 按 group_id 查询全部声纹（本地库全量视角）。 */
    @GetMapping("/features/all")
    public ApiResponse<JsonNode> allFeaturesByGroupId(@RequestParam(required = false) String groupId) {
        JsonNode data = meetingServerBridgeService.queryInternalVoiceprintAllFeaturesByGroupId(groupId);
        return ApiResponse.ok(requireData(data, "按 group_id 查询全部声纹失败：meeting-server 返回空结果"));
    }

    /** P0: 删除指定特征（admin）。 */
    @DeleteMapping("/features/{featureId}")
    public ApiResponse<JsonNode> deleteFeature(@PathVariable String featureId) {
        JsonNode data = meetingServerBridgeService.deleteInternalVoiceprintFeature(featureId);
        return ApiResponse.ok(requireData(data, "删除声纹特征失败：meeting-server 返回空结果"));
    }

    /** P0: 更新指定特征（覆盖/合并）。 */
    @PostMapping("/features/{featureId}/update")
    public ApiResponse<JsonNode> updateFeature(@PathVariable String featureId, @RequestBody UpdateFeatureBody body) {
        JsonNode data = meetingServerBridgeService.updateInternalVoiceprintFeature(
                body.getGroupId(), featureId, body.getFeatureInfo(), body.getAudioBase64(), body.isCover());
        return ApiResponse.ok(requireData(data, "更新声纹特征失败：meeting-server 返回空结果"));
    }

    /** P1: 1:N 检索。 */
    @PostMapping("/search/1n")
    public ApiResponse<JsonNode> search1N(@RequestBody Search1NBody body) {
        JsonNode data = meetingServerBridgeService.searchInternalVoiceprint1N(body.getGroupId(), body.getAudioBase64(), body.getTopK());
        return ApiResponse.ok(requireData(data, "1:N 检索失败：meeting-server 返回空结果"));
    }

    /** P2: 1:1 比对。 */
    @PostMapping("/search/1v1")
    public ApiResponse<JsonNode> search1V1(@RequestBody Search1V1Body body) {
        JsonNode data = meetingServerBridgeService.searchInternalVoiceprint1V1(
                body.getGroupId(), body.getFeatureId(), body.getAudioBase64());
        return ApiResponse.ok(requireData(data, "1:1 比对失败：meeting-server 返回空结果"));
    }

    /** P2: 创建特征库。 */
    @PostMapping("/groups")
    public ApiResponse<JsonNode> createGroup(@RequestBody GroupCreateBody body) {
        JsonNode data = meetingServerBridgeService.createInternalVoiceprintGroup(
                body.getGroupId(), body.getGroupName(), body.getGroupInfo());
        return ApiResponse.ok(requireData(data, "创建特征库失败：meeting-server 返回空结果"));
    }

    /** P2: 删除特征库。 */
    @DeleteMapping("/groups/{groupId}")
    public ApiResponse<JsonNode> deleteGroup(@PathVariable String groupId) {
        JsonNode data = meetingServerBridgeService.deleteInternalVoiceprintGroup(groupId);
        return ApiResponse.ok(requireData(data, "删除特征库失败：meeting-server 返回空结果"));
    }

    private JsonNode requireData(JsonNode data, String message) {
        if (data == null || data.isNull()) {
            throw new BusinessException(502, message);
        }
        return data;
    }

    public static class UpdateFeatureBody {
        private String groupId;
        private String featureInfo;
        private String audioBase64;
        private boolean cover = true;
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getFeatureInfo() { return featureInfo; }
        public void setFeatureInfo(String featureInfo) { this.featureInfo = featureInfo; }
        public String getAudioBase64() { return audioBase64; }
        public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
        public boolean isCover() { return cover; }
        public void setCover(boolean cover) { this.cover = cover; }
    }

    public static class Search1NBody {
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

    public static class Search1V1Body {
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

    public static class GroupCreateBody {
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
}

