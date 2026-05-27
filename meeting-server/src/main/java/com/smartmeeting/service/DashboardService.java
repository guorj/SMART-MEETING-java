package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.UserMappingMapper;
import com.smartmeeting.repository.VoiceprintMapper;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 飞书 Dashboard 工作台业务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int EXPIRING_HOURS = 48;

    private final UserMappingMapper userMappingMapper;
    private final VoiceprintMapper voiceprintMapper;
    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingTypePresetService presetService;
    private final FeishuMeetingStartCoordinator coordinator;
    private final FeishuStartMeetingPendingStore pendingStore;
    private final FeishuService feishuService;
    private final VoiceprintRegisterService voiceprintRegisterService;

    public UserInfo getUserInfo(String openId) {
        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuOpenId, openId)
                .last("LIMIT 1"));
        Voiceprint vp = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, openId)
                .orderByDesc(Voiceprint::getRegisteredAt)
                .last("LIMIT 1"));

        UserInfo info = new UserInfo();
        info.setFeishuOpenId(openId);
        if (mapping != null) {
            info.setOaUserId(mapping.getUserId());
            info.setUserName(mapping.getUserName());
            info.setFeishuUserId(mapping.getFeishuUserId());
            info.setMappingExists(true);
        } else {
            info.setUserName(feishuService.getUserName(openId));
            info.setMappingExists(false);
        }
        if (vp != null) {
            info.setVoiceprintRegistered(true);
            info.setVoiceprintFeatureId(vp.getFeatureId());
            info.setVoiceprintExpiresAt(vp.getExpiresAt());
            info.setVoiceprintExpiryStatus(resolveExpiryStatus(vp.getExpiresAt()));
        } else {
            info.setVoiceprintRegistered(false);
            info.setVoiceprintExpiryStatus("NONE");
        }
        return info;
    }

    public VoiceprintStatusResult getVoiceprintStatus(String openId) {
        Voiceprint vp = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, openId)
                .orderByDesc(Voiceprint::getRegisteredAt)
                .last("LIMIT 1"));
        VoiceprintStatusResult result = new VoiceprintStatusResult();
        if (vp == null) {
            result.setRegistered(false);
            result.setExpiryStatus("NONE");
            return result;
        }
        result.setRegistered(true);
        result.setFeatureId(vp.getFeatureId());
        result.setExpiresAt(vp.getExpiresAt());
        result.setExpiryStatus(resolveExpiryStatus(vp.getExpiresAt()));
        return result;
    }

    public List<MeetingSummary> getRecentMeetings(String openId, int limit) {
        List<String> meetingIds = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                        .eq(Participant::getUserId, openId))
                .stream().map(Participant::getMeetingId).distinct().toList();

        List<Meeting> meetings;
        if (meetingIds.isEmpty()) {
            meetings = meetingMapper.selectList(new LambdaQueryWrapper<Meeting>()
                    .eq(Meeting::getCreatorId, openId)
                    .orderByDesc(Meeting::getCreatedAt)
                    .last("LIMIT " + limit));
        } else {
            meetings = meetingMapper.selectList(new LambdaQueryWrapper<Meeting>()
                    .in(Meeting::getId, meetingIds)
                    .orderByDesc(Meeting::getCreatedAt)
                    .last("LIMIT " + limit));
        }
        return meetings.stream().map(this::toMeetingSummary).toList();
    }

    public List<MeetingPresetResponse> getMeetingPresets() {
        List<MeetingPresetResponse> list = new ArrayList<>(presetService.listPresets());
        list.add(MeetingPresetResponse.builder()
                .code(6)
                .displayName("其他会议（需填写会议主题）")
                .company(MeetingTypePresetService.DEFAULT_COMPANY)
                .groupName(MeetingTypePresetService.OTHER_GROUP)
                .agendaSummary("自定义主题与议程")
                .participantNames(List.of())
                .build());
        return list;
    }

    public MeetingResponse createMeeting(String openId, String chatId, MeetingCreateRequest request) {
        pendingStore.clear(openId, chatId);
        return coordinator.createMeetingStartAndNotifyFeishu(openId, chatId, request);
    }

    public String createVoiceprintSession(String openId, String userName) {
        String resolvedName = (userName != null && !userName.isBlank()) ? userName : feishuService.getUserName(openId);
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = "用户" + openId.substring(Math.max(0, openId.length() - 6));
        }
        String regToken = UUID.randomUUID().toString().replace("-", "");
        voiceprintRegisterService.createRegisterSession(regToken, openId, resolvedName);
        return regToken;
    }

    private MeetingSummary toMeetingSummary(Meeting m) {
        MeetingSummary s = new MeetingSummary();
        s.setId(m.getId());
        s.setTitle(m.getTitle());
        s.setStatus(m.getStatus());
        s.setPresetTypeCode(m.getPresetTypeCode());
        s.setCreatedAt(m.getCreatedAt());
        s.setDocUrl(m.getDocUrl());
        return s;
    }

    private String resolveExpiryStatus(LocalDateTime expiresAt) {
        if (expiresAt == null) return "UNKNOWN";
        LocalDateTime now = LocalDateTime.now();
        if (!expiresAt.isAfter(now)) return "EXPIRED";
        if (!expiresAt.isAfter(now.plusHours(EXPIRING_HOURS))) return "EXPIRING";
        return "VALID";
    }

    public static class UserInfo {
        private Integer oaUserId;
        private String userName;
        private String feishuUserId;
        private String feishuOpenId;
        private boolean mappingExists;
        private boolean voiceprintRegistered;
        private String voiceprintFeatureId;
        private LocalDateTime voiceprintExpiresAt;
        private String voiceprintExpiryStatus;

        public Integer getOaUserId() { return oaUserId; }
        public void setOaUserId(Integer oaUserId) { this.oaUserId = oaUserId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getFeishuUserId() { return feishuUserId; }
        public void setFeishuUserId(String feishuUserId) { this.feishuUserId = feishuUserId; }
        public String getFeishuOpenId() { return feishuOpenId; }
        public void setFeishuOpenId(String feishuOpenId) { this.feishuOpenId = feishuOpenId; }
        public boolean isMappingExists() { return mappingExists; }
        public void setMappingExists(boolean mappingExists) { this.mappingExists = mappingExists; }
        public boolean isVoiceprintRegistered() { return voiceprintRegistered; }
        public void setVoiceprintRegistered(boolean voiceprintRegistered) { this.voiceprintRegistered = voiceprintRegistered; }
        public String getVoiceprintFeatureId() { return voiceprintFeatureId; }
        public void setVoiceprintFeatureId(String voiceprintFeatureId) { this.voiceprintFeatureId = voiceprintFeatureId; }
        public LocalDateTime getVoiceprintExpiresAt() { return voiceprintExpiresAt; }
        public void setVoiceprintExpiresAt(LocalDateTime voiceprintExpiresAt) { this.voiceprintExpiresAt = voiceprintExpiresAt; }
        public String getVoiceprintExpiryStatus() { return voiceprintExpiryStatus; }
        public void setVoiceprintExpiryStatus(String voiceprintExpiryStatus) { this.voiceprintExpiryStatus = voiceprintExpiryStatus; }
    }

    public static class MeetingSummary {
        private String id;
        private String title;
        private String status;
        private Integer presetTypeCode;
        private LocalDateTime createdAt;
        private String docUrl;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getPresetTypeCode() { return presetTypeCode; }
        public void setPresetTypeCode(Integer presetTypeCode) { this.presetTypeCode = presetTypeCode; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public String getDocUrl() { return docUrl; }
        public void setDocUrl(String docUrl) { this.docUrl = docUrl; }
    }

    public static class VoiceprintStatusResult {
        private boolean registered;
        private String featureId;
        private LocalDateTime expiresAt;
        private String expiryStatus;

        public boolean isRegistered() { return registered; }
        public void setRegistered(boolean registered) { this.registered = registered; }
        public String getFeatureId() { return featureId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public String getExpiryStatus() { return expiryStatus; }
        public void setExpiryStatus(String expiryStatus) { this.expiryStatus = expiryStatus; }
    }
}