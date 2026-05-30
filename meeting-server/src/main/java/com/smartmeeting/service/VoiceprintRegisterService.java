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

        UserMapping mapping = userMappingMapper.selectOne(new LambdaQueryWrapper<UserMapping>()
                .eq(UserMapping::getFeishuUserId, feishuUserId)
                .last("LIMIT 1"));
        if (mapping == null || mapping.getUserId() == null) {
            log.warn("声纹注册中止：缺少 user_id 映射, token={}, feishuUserId={}", token, feishuUserId);
            return new RegisterResult(false, "未找到当前飞书账号对应的系统用户，请先在用户映射中维护 feishu_user_id", null);
        }

        String featureId = isvClient.registerVoiceprint(feishuUserId, userName, audioData);
        if (featureId == null) {
            log.warn("声纹注册失败: token={}, feishuUserId={}, costMs={}",
                    token, feishuUserId, System.currentTimeMillis() - start);
            return new RegisterResult(false, "声纹注册失败（音频格式或ISV调用异常），请刷新页面后重新录制", null);
        }

        Voiceprint existing = voiceprintMapper.selectOne(
            new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, feishuUserId));
        Voiceprint existingSnapshot = copy(existing);
        boolean hadExisting = existing != null;
        Voiceprint target = hadExisting ? existing : new Voiceprint();
        if (!hadExisting) {
            target.setId(UUID.randomUUID().toString());
        }
        target.setUserId(mapping.getUserId());
        target.setUserName(userName);
        target.setFeishuUserId(feishuUserId);
        target.setFeatureId(featureId);
        target.setGroupId(isvClient.getGroupId());
        target.setRegisteredAt(LocalDateTime.now());
        target.setExpiresAt(LocalDateTime.now().plusYears(VOICEPRINT_EXPIRE_YEARS));

        try {
            if (hadExisting) {
                voiceprintMapper.updateById(target);
            } else {
                voiceprintMapper.insert(target);
            }
        } catch (Exception e) {
            log.error("声纹入库失败，开始回滚讯飞特征: feishuUserId={}, featureId={}", feishuUserId, featureId, e);
            try {
                isvClient.deleteVoiceprint(featureId);
            } catch (Exception rollbackEx) {
                log.error("回滚讯飞特征失败: featureId={}", featureId, rollbackEx);
            }
            return new RegisterResult(false, "声纹注册入库失败，请联系管理员检查数据库约束", null);
        }

        if (existingSnapshot != null
                && existingSnapshot.getFeatureId() != null
                && !existingSnapshot.getFeatureId().isBlank()
                && !existingSnapshot.getFeatureId().equals(featureId)) {
            boolean deletedOld = isvClient.deleteVoiceprint(existingSnapshot.getFeatureId());
            if (!deletedOld) {
                log.error("清理旧声纹失败，开始回滚本地和新特征: feishuUserId={}, oldFeatureId={}, newFeatureId={}",
                        feishuUserId, existingSnapshot.getFeatureId(), featureId);
                try {
                    if (hadExisting) {
                        voiceprintMapper.updateById(existingSnapshot);
                    } else {
                        voiceprintMapper.deleteById(target.getId());
                    }
                } catch (Exception dbRollbackEx) {
                    log.error("本地声纹回滚失败: feishuUserId={}, newFeatureId={}", feishuUserId, featureId, dbRollbackEx);
                }
                try {
                    isvClient.deleteVoiceprint(featureId);
                } catch (Exception remoteRollbackEx) {
                    log.error("新特征回滚失败: featureId={}", featureId, remoteRollbackEx);
                }
                return new RegisterResult(false, "旧声纹清理失败，本次注册已回滚，请稍后重试", null);
            }
            log.info("旧声纹已清理: feishuUserId={}, oldFeatureId={}", feishuUserId, existingSnapshot.getFeatureId());
        }

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

    private Voiceprint copy(Voiceprint source) {
        if (source == null) {
            return null;
        }
        Voiceprint out = new Voiceprint();
        out.setId(source.getId());
        out.setUserId(source.getUserId());
        out.setUserName(source.getUserName());
        out.setFeishuUserId(source.getFeishuUserId());
        out.setFeatureId(source.getFeatureId());
        out.setGroupId(source.getGroupId());
        out.setRegisteredAt(source.getRegisteredAt());
        out.setExpiresAt(source.getExpiresAt());
        return out;
    }
}
