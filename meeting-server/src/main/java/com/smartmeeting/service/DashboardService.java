package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.UserMappingMapper;
import com.smartmeeting.config.MeetingVoiceprintLifecycleProperties;
import com.smartmeeting.repository.VoiceprintMapper;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
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

    private final UserMappingMapper userMappingMapper;
    private final VoiceprintMapper voiceprintMapper;
    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingTypePresetService presetService;
    private final FeishuMeetingStartCoordinator coordinator;
    private final FeishuStartMeetingPendingStore pendingStore;
    private final FeishuService feishuService;
    private final VoiceprintRegisterService voiceprintRegisterService;
    private final MeetingService meetingService;
    private final JwtUtil jwtUtil;
    private final MeetingVoiceprintLifecycleProperties lifecycleProperties;
    private final MeetingWebPageUrls meetingWebPageUrls;

    public UserInfo getUserInfo(String feishuUserId, String tokenUserName) {
        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, feishuUserId)
                .last("LIMIT 1"));
        Voiceprint vp = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, feishuUserId)
                .orderByDesc(Voiceprint::getRegisteredAt)
                .last("LIMIT 1"));

        UserInfo info = new UserInfo();
        info.setFeishuUserId(feishuUserId);
        String resolvedFromFeishu = null;
        if (mapping != null) {
            info.setOaUserId(mapping.getUserId());
            resolvedFromFeishu = resolveNameFromFeishu(mapping, feishuUserId);
            info.setUserName(normalizeDisplayName(mapping.getUserName(), resolvedFromFeishu, tokenUserName));
            info.setMappingExists(true);
            log.info("Dashboard user resolve with mapping: feishuUserId={}, oaUserId={}, mappingName={}, feishuResolved={}, tokenName={}",
                    feishuUserId, mapping.getUserId(), mapping.getUserName(), resolvedFromFeishu, tokenUserName);
        } else {
            String userName = resolveNameFromFeishu(null, feishuUserId);
            info.setUserName(normalizeDisplayName(userName, tokenUserName));
            info.setMappingExists(false);
            log.warn("Dashboard user resolve without mapping: feishuUserId={}, feishuResolved={}, tokenName={}",
                    feishuUserId, userName, tokenUserName);
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

    public VoiceprintStatusResult getVoiceprintStatus(String feishuUserId) {
        Voiceprint vp = voiceprintMapper.selectOne(new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, feishuUserId)
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

    public List<MeetingSummary> getRecentMeetings(String feishuUserId, int limit) {
        List<String> meetingIds = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                        .eq(Participant::getUserId, feishuUserId))
                .stream().map(Participant::getMeetingId).distinct().toList();

        List<Meeting> meetings;
        if (meetingIds.isEmpty()) {
            meetings = meetingMapper.selectList(new LambdaQueryWrapper<Meeting>()
                    .eq(Meeting::getCreatorId, feishuUserId)
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
        return new ArrayList<>(presetService.listPresets());
    }

    public ActiveMeetingResult getActiveMeeting(String feishuUserId) {
        Meeting active = meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getCreatorId, feishuUserId)
                .in(Meeting::getStatus,
                        MeetingStatus.STARTED.name(),
                        MeetingStatus.RECORDING.name(),
                        MeetingStatus.PAUSED.name())
                .orderByDesc(Meeting::getCreatedAt)
                .last("LIMIT 1"));
        if (active == null) {
            return null;
        }
        String recordingToken = active.getRecordingToken();
        if (recordingToken == null || recordingToken.isBlank()) {
            String userName = feishuService.getUserNameByUserId(feishuUserId);
            recordingToken = jwtUtil.generateOperatorMeetingToken(
                    active.getId(), JwtUtil.TYPE_RECORDING, feishuUserId, userName);
            meetingMapper.update(null, new LambdaUpdateWrapper<Meeting>()
                    .eq(Meeting::getId, active.getId())
                    .set(Meeting::getRecordingToken, recordingToken));
        }
        String recordingUrl = meetingWebPageUrls.resolveRecordingPageUrl(
                active.getId(), recordingToken, active.getRecordingUrl());
        ActiveMeetingResult out = new ActiveMeetingResult();
        out.setId(active.getId());
        out.setTitle(active.getTitle());
        out.setStatus(active.getStatus());
        out.setRecordingUrl(recordingUrl);
        out.setCreatedAt(active.getCreatedAt());
        return out;
    }

    public MeetingResponse endActiveMeeting(String feishuUserId) {
        Meeting active = meetingMapper.selectOne(new LambdaQueryWrapper<Meeting>()
                .eq(Meeting::getCreatorId, feishuUserId)
                .in(Meeting::getStatus,
                        MeetingStatus.STARTED.name(),
                        MeetingStatus.RECORDING.name(),
                        MeetingStatus.PAUSED.name())
                .orderByDesc(Meeting::getCreatedAt)
                .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException(400, "当前没有可结束的进行中会议");
        }
        return meetingService.endMeeting(active.getId());
    }

    public ActiveMeetingResult recoverActiveMeeting(String feishuUserId) {
        ActiveMeetingResult active = getActiveMeeting(feishuUserId);
        if (active == null || active.getRecordingUrl() == null || active.getRecordingUrl().isBlank()) {
            throw new BusinessException(400, "当前没有可恢复的进行中会议");
        }
        return active;
    }

    private String resolveNameFromFeishu(UserMapping mapping, String feishuUserId) {
        String[] candidates = new String[] {
                feishuUserId,
                mapping != null ? mapping.getFeishuUserId() : null
        };
        for (String id : candidates) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String name = feishuService.getUserNameByUserId(id);
            if (sanitizeName(name) != null) {
                return name;
            }
        }
        return null;
    }

    private static String normalizeDisplayName(String... candidates) {
        if (candidates == null) {
            return "飞书用户";
        }
        for (String c : candidates) {
            String v = sanitizeName(c);
            if (v != null) {
                return v;
            }
        }
        return "飞书用户";
    }

    private static String sanitizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.startsWith("ou_") || v.startsWith("on_")) {
            return null;
        }
        return v;
    }

    public MeetingResponse createMeeting(String feishuUserId, String chatId, MeetingCreateRequest request) {
        pendingStore.clear(feishuUserId, chatId);
        return coordinator.createMeetingStartAndNotifyFeishu(feishuUserId, chatId, request);
    }

    public String createVoiceprintSession(String feishuUserId, String userName) {
        String resolvedName = userName;
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = feishuService.getUserNameByUserId(feishuUserId);
        }
        if (resolvedName == null || resolvedName.isBlank()) {
            resolvedName = "用户" + feishuUserId.substring(Math.max(0, feishuUserId.length() - 6));
        }
        String regToken = UUID.randomUUID().toString().replace("-", "");
        voiceprintRegisterService.createRegisterSession(regToken, feishuUserId, resolvedName);
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
        if (!expiresAt.isAfter(now.plusHours(lifecycleProperties.getExpiringWarningHours()))) return "EXPIRING";
        return "VALID";
    }

    public static class UserInfo {
        private Integer oaUserId;
        private String userName;
        private String feishuUserId;
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

    public static class ActiveMeetingResult {
        private String id;
        private String title;
        private String status;
        private String recordingUrl;
        private LocalDateTime createdAt;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRecordingUrl() { return recordingUrl; }
        public void setRecordingUrl(String recordingUrl) { this.recordingUrl = recordingUrl; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
