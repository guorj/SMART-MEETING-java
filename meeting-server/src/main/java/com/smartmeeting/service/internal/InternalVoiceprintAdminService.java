package com.smartmeeting.service.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalVoiceprintAdminService {

    private final XfyunIsvClient xfyunIsvClient;
    private final VoiceprintMapper voiceprintMapper;

    public List<FeatureView> queryFeatureList(String groupId) {
        List<XfyunIsvClient.FeatureItem> remote = xfyunIsvClient.queryFeatureList(groupId);
        List<FeatureView> out = new ArrayList<>();
        for (XfyunIsvClient.FeatureItem item : remote) {
            Voiceprint local = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                    .eq(Voiceprint::getFeatureId, item.featureId)
                    .last("LIMIT 1"));
            FeatureView v = new FeatureView();
            v.setFeatureId(item.featureId);
            v.setFeatureInfo(item.featureInfo);
            if (local != null) {
                v.setUserId(local.getUserId());
                v.setUserName(local.getUserName());
                v.setFeishuUserId(local.getFeishuUserId());
                v.setRegisteredAt(local.getRegisteredAt());
                v.setExpiresAt(local.getExpiresAt());
            }
            out.add(v);
        }
        return out;
    }

    /**
     * 按 group_id 查询本地数据库中全部声纹记录（不依赖讯飞返回）。
     */
    public List<FeatureView> queryAllLocalFeaturesByGroupId(String groupId) {
        LambdaQueryWrapper<Voiceprint> wrapper = new LambdaQueryWrapper<>();
        if (groupId != null && !groupId.isBlank()) {
            wrapper.eq(Voiceprint::getGroupId, groupId.trim());
        }
        wrapper.orderByDesc(Voiceprint::getRegisteredAt);
        List<Voiceprint> locals = voiceprintMapper.selectList(wrapper);
        List<FeatureView> out = new ArrayList<>();
        for (Voiceprint local : locals) {
            FeatureView v = new FeatureView();
            v.setFeatureId(local.getFeatureId());
            v.setFeatureInfo("");
            v.setUserId(local.getUserId());
            v.setUserName(local.getUserName());
            v.setFeishuUserId(local.getFeishuUserId());
            v.setRegisteredAt(local.getRegisteredAt());
            v.setExpiresAt(local.getExpiresAt());
            out.add(v);
        }
        return out;
    }

    public boolean deleteFeature(String featureId) {
        boolean ok = xfyunIsvClient.deleteVoiceprint(featureId);
        if (ok) {
            voiceprintMapper.delete(new LambdaQueryWrapper<Voiceprint>().eq(Voiceprint::getFeatureId, featureId));
        }
        return ok;
    }

    public boolean createGroup(String groupId, String groupName, String groupInfo) {
        return xfyunIsvClient.createGroup(groupId, groupName, groupInfo);
    }

    public boolean deleteGroup(String groupId) {
        return xfyunIsvClient.deleteGroup(groupId);
    }

    public boolean updateFeature(String groupId, String featureId, String featureInfo, byte[] audioData, boolean cover) {
        boolean ok = xfyunIsvClient.updateFeature(groupId, featureId, featureInfo, audioData, cover);
        if (ok) {
            Voiceprint local = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                    .eq(Voiceprint::getFeatureId, featureId)
                    .last("LIMIT 1"));
            if (local != null) {
                if (featureInfo != null && !featureInfo.isBlank()) {
                    local.setUserName(featureInfo);
                }
                local.setRegisteredAt(LocalDateTime.now());
                voiceprintMapper.updateById(local);
            }
        }
        return ok;
    }

    public List<ScoreView> search1N(String groupId, byte[] audioData, int topK) {
        List<XfyunIsvClient.SearchScoreItem> list = xfyunIsvClient.search1N(groupId, audioData, topK);
        return mapScoreViews(list);
    }

    public ScoreView search1V1(String groupId, String targetFeatureId, byte[] audioData) {
        XfyunIsvClient.SearchScoreItem item = xfyunIsvClient.search1V1(groupId, targetFeatureId, audioData);
        if (item == null) {
            return null;
        }
        List<ScoreView> mapped = mapScoreViews(List.of(item));
        return mapped.isEmpty() ? null : mapped.get(0);
    }

    private List<ScoreView> mapScoreViews(List<XfyunIsvClient.SearchScoreItem> list) {
        List<ScoreView> out = new ArrayList<>();
        for (XfyunIsvClient.SearchScoreItem item : list) {
            Voiceprint local = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                    .eq(Voiceprint::getFeatureId, item.featureId)
                    .last("LIMIT 1"));
            ScoreView v = new ScoreView();
            v.setFeatureId(item.featureId);
            v.setFeatureInfo(item.featureInfo);
            v.setScore(item.score);
            if (local != null) {
                v.setUserId(local.getUserId());
                v.setUserName(local.getUserName());
                v.setFeishuUserId(local.getFeishuUserId());
            }
            out.add(v);
        }
        return out;
    }

    public static class FeatureView {
        private String featureId;
        private String featureInfo;
        private Integer userId;
        private String userName;
        private String feishuUserId;
        private LocalDateTime registeredAt;
        private LocalDateTime expiresAt;

        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public String getFeatureInfo() { return featureInfo; }
        public void setFeatureInfo(String featureInfo) { this.featureInfo = featureInfo; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getFeishuUserId() { return feishuUserId; }
        public void setFeishuUserId(String feishuUserId) { this.feishuUserId = feishuUserId; }
        public LocalDateTime getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class ScoreView {
        private String featureId;
        private String featureInfo;
        private double score;
        private Integer userId;
        private String userName;
        private String feishuUserId;

        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public String getFeatureInfo() { return featureInfo; }
        public void setFeatureInfo(String featureInfo) { this.featureInfo = featureInfo; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getFeishuUserId() { return feishuUserId; }
        public void setFeishuUserId(String feishuUserId) { this.feishuUserId = feishuUserId; }
    }
}

