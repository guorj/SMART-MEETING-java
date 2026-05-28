package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.entity.UserMapping;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.UserMappingMapper;
import com.smartmeeting.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书用户声纹注册流程服务（Web 页 + 一次性 token）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceprintRegisterService {

    private final XfyunIsvClient isvClient;
    private final VoiceprintMapper voiceprintMapper;
    private final UserMappingMapper userMappingMapper;

    private final Map<String, RegisterSession> pendingSessions = new ConcurrentHashMap<>();

    private static final int SESSION_EXPIRE_MINUTES = 30;
    private static final int VOICEPRINT_EXPIRE_YEARS = 10;

    public void createRegisterSession(String token, String feishuUserId, String userName) {
        pendingSessions.entrySet().removeIf(e ->
            e.getValue().getFeishuUserId().equals(feishuUserId));

        RegisterSession session = new RegisterSession();
        session.setToken(token);
        session.setFeishuUserId(feishuUserId);
        session.setUserName(userName);
        session.setCreatedAt(LocalDateTime.now());

        pendingSessions.put(token, session);
        log.info("注册session已创建: token={}, feishuUserId={}, userName={}", token, feishuUserId, userName);
    }

    public RegisterSession getRegisterSession(String token) {
        RegisterSession session = pendingSessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.getCreatedAt().plusMinutes(SESSION_EXPIRE_MINUTES)
                .isBefore(LocalDateTime.now())) {
            pendingSessions.remove(token);
            log.warn("注册session已过期: token={}", token);
            return null;
        }
        return session;
    }

    public RegisterSession consumeRegisterSession(String token) {
        RegisterSession session = getRegisterSession(token);
        if (session != null) {
            pendingSessions.remove(token);
        }
        return session;
    }

    public RegisterResult submitRegister(String token, byte[] audioData) {
        RegisterSession session = getRegisterSession(token);
        if (session == null) {
            return new RegisterResult(false, "注册链接已过期或无效", null);
        }

        if (audioData == null || audioData.length < 1024) {
            return new RegisterResult(false, "音频数据无效，请重新录制", null);
        }

        String feishuUserId = session.getFeishuUserId();
        String userName = session.getUserName();
        long start = System.currentTimeMillis();
        log.info("声纹注册开始: token={}, feishuUserId={}, audioBytes={}", token, feishuUserId, audioData.length);

        String featureId = isvClient.registerVoiceprint(feishuUserId, userName, audioData);
        if (featureId == null) {
            log.warn("声纹注册失败: token={}, feishuUserId={}, costMs={}",
                    token, feishuUserId, System.currentTimeMillis() - start);
            return new RegisterResult(false, "声纹注册失败（ISV API调用失败）", null);
        }

        Voiceprint voiceprint = new Voiceprint();
        voiceprint.setId(UUID.randomUUID().toString());
        voiceprint.setUserId(null);
        voiceprint.setUserName(userName);
        voiceprint.setFeishuUserId(feishuUserId);
        voiceprint.setFeatureId(featureId);
        voiceprint.setGroupId(isvClient.getGroupId());
        voiceprint.setRegisteredAt(LocalDateTime.now());
        voiceprint.setExpiresAt(LocalDateTime.now().plusYears(VOICEPRINT_EXPIRE_YEARS));

        Voiceprint existing = voiceprintMapper.selectOne(
            new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, feishuUserId));
        if (existing != null) {
            voiceprintMapper.deleteById(existing.getId());
            log.info("旧声纹已删除: feishuUserId={}, oldFeatureId={}", feishuUserId, existing.getFeatureId());
        }

        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, feishuUserId)
                .last("LIMIT 1"));
        if (mapping != null && mapping.getUserId() != null) {
            voiceprint.setUserId(mapping.getUserId());
        }

        voiceprintMapper.insert(voiceprint);
        pendingSessions.remove(token);
        log.info("声纹注册成功: feishuUserId={}, userName={}, featureId={}, costMs={}",
                feishuUserId, userName, featureId, System.currentTimeMillis() - start);

        return new RegisterResult(true, "声纹注册成功！", featureId);
    }

    public VoiceprintStatus checkVoiceprintStatus(String feishuUserId) {
        Voiceprint vp = voiceprintMapper.selectOne(
            new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, feishuUserId));

        if (vp == null) {
            return new VoiceprintStatus(false, false, null);
        }

        LocalDateTime expiresAt = vp.getExpiresAt();
        LocalDateTime now = LocalDateTime.now();

        boolean isValid = expiresAt.isAfter(now);
        boolean isExpiring = expiresAt.isBefore(now.plusDays(30));

        return new VoiceprintStatus(isValid, isExpiring, expiresAt);
    }

    public static class RegisterSession {
        private String token;
        private String feishuUserId;
        private String userName;
        private LocalDateTime createdAt;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getFeishuUserId() { return feishuUserId; }
        public void setFeishuUserId(String feishuUserId) { this.feishuUserId = feishuUserId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class RegisterResult {
        private final boolean success;
        private final String message;
        private final String featureId;

        public RegisterResult(boolean success, String message, String featureId) {
            this.success = success;
            this.message = message;
            this.featureId = featureId;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getFeatureId() { return featureId; }
    }

    public static class VoiceprintStatus {
        private final boolean isValid;
        private final boolean isExpiring;
        private final LocalDateTime expiresAt;

        public VoiceprintStatus(boolean isValid, boolean isExpiring, LocalDateTime expiresAt) {
            this.isValid = isValid;
            this.isExpiring = isExpiring;
            this.expiresAt = expiresAt;
        }

        public boolean isValid() { return isValid; }
        public boolean isExpiring() { return isExpiring; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}
