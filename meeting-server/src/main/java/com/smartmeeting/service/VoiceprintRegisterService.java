package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书用户声纹注册流程服务（Web 页 + 一次性 token）。
 *
 * <p>流程：创建内存 {@link RegisterSession} → 用户上传 PCM → 调用 {@link XfyunIsvClient} 注册 →
 * 写入 {@code int_voiceprint} 表。Session 存于进程内存，重启后失效。
 *
 * <p>主要协作：{@link XfyunIsvClient}、{@link VoiceprintMapper}；
 * 由 {@link FeishuCommandHandler} 下发注册链接。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceprintRegisterService {

    private final XfyunIsvClient isvClient;
    private final VoiceprintMapper voiceprintMapper;
    
    // 注册session存储（内存，重启丢失）
    private final Map<String, RegisterSession> pendingSessions = new ConcurrentHashMap<>();
    
    // session有效期30分钟
    private static final int SESSION_EXPIRE_MINUTES = 30;
    
    // 声纹有效期10年
    private static final int VOICEPRINT_EXPIRE_YEARS = 10;
    
    /**
     * 创建注册session
     * 
     * @param token 注册token（由调用方生成）
     * @param openId 飞书用户open_id
     * @param userName 用户姓名
     */
    public void createRegisterSession(String token, String openId, String userName) {
        // 清除该openId的旧session（防止重复）
        pendingSessions.entrySet().removeIf(e -> 
            e.getValue().getOpenId().equals(openId));
        
        RegisterSession session = new RegisterSession();
        session.setToken(token);
        session.setOpenId(openId);
        session.setUserName(userName);
        session.setCreatedAt(LocalDateTime.now());
        
        pendingSessions.put(token, session);
        log.info("注册session已创建: token={}, openId={}, userName={}", token, openId, userName);
    }
    
    /**
     * 获取未过期的注册 session（不消费）。
     *
     * @param token 注册令牌
     * @return session；不存在或已过期时返回 {@code null}
     */
    public RegisterSession getRegisterSession(String token) {
        RegisterSession session = pendingSessions.get(token);
        if (session == null) {
            return null;
        }
        
        // 检查是否过期
        if (session.getCreatedAt().plusMinutes(SESSION_EXPIRE_MINUTES)
                .isBefore(LocalDateTime.now())) {
            pendingSessions.remove(token);
            log.warn("注册session已过期: token={}", token);
            return null;
        }
        
        return session;
    }
    
    /**
     * 获取并移除注册 session（一次性，用于提交音频）。
     *
     * @param token 注册令牌
     * @return session；不存在或已过期时返回 {@code null}
     */
    public RegisterSession consumeRegisterSession(String token) {
        RegisterSession session = getRegisterSession(token);
        if (session != null) {
            pendingSessions.remove(token);
        }
        return session;
    }
    
    /**
     * 提交声纹注册（接收音频）
     * 
     * @param token 注册token
     * @param audioData PCM 音频数据（16kHz 单声道，建议不少于 5 秒）
     * @return 注册结果（成功含 featureId）
     */
    public RegisterResult submitRegister(String token, byte[] audioData) {
        RegisterSession session = consumeRegisterSession(token);
        if (session == null) {
            return new RegisterResult(false, "注册链接已过期或无效", null);
        }
        
        // 检查音频长度（建议至少5秒 = 80000字节，16kHz*16bit*1ch=32000bytes/sec）
        if (audioData.length < 32000 * 5) {
            return new RegisterResult(false, "音频时长不足5秒，请重新录制", null);
        }
        
        String openId = session.getOpenId();
        String userName = session.getUserName();
        
        // 调用ISV注册
        String featureId = isvClient.registerVoiceprint(openId, userName, audioData);
        if (featureId == null) {
            return new RegisterResult(false, "声纹注册失败（ISV API调用失败）", null);
        }
        
        // 写入数据库
        Voiceprint voiceprint = new Voiceprint();
        voiceprint.setId(UUID.randomUUID().toString());
        voiceprint.setUserId(null); // OA userId未知，暂不填
        voiceprint.setUserName(userName);
        voiceprint.setFeishuUserId(openId);
        voiceprint.setFeatureId(featureId);
        voiceprint.setGroupId(isvClient.getGroupId());
        voiceprint.setRegisteredAt(LocalDateTime.now());
        voiceprint.setExpiresAt(LocalDateTime.now().plusYears(VOICEPRINT_EXPIRE_YEARS));
        
        // 如果已有旧声纹，先删除
        Voiceprint existing = voiceprintMapper.selectOne(
            new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, openId));
        if (existing != null) {
            voiceprintMapper.deleteById(existing.getId());
            log.info("旧声纹已删除: openId={}, oldFeatureId={}", openId, existing.getFeatureId());
        }
        
        voiceprintMapper.insert(voiceprint);
        log.info("声纹注册成功: openId={}, userName={}, featureId={}", openId, userName, featureId);
        
        return new RegisterResult(true, "声纹注册成功！", featureId);
    }
    
    /**
     * 检查飞书用户声纹是否有效及是否临近过期。
     *
     * @param openId 飞书 open_id
     * @return 声纹状态摘要
     */
    public VoiceprintStatus checkVoiceprintStatus(String openId) {
        Voiceprint vp = voiceprintMapper.selectOne(
            new LambdaQueryWrapper<Voiceprint>()
                .eq(Voiceprint::getFeishuUserId, openId));
        
        if (vp == null) {
            return new VoiceprintStatus(false, false, null);
        }
        
        LocalDateTime expiresAt = vp.getExpiresAt();
        LocalDateTime now = LocalDateTime.now();
        
        boolean isValid = expiresAt.isAfter(now);
        boolean isExpiring = expiresAt.isBefore(now.plusDays(30));
        
        return new VoiceprintStatus(isValid, isExpiring, expiresAt);
    }
    
    /** 进行中的声纹注册会话（内存态）。 */
    public static class RegisterSession {
        private String token;
        private String openId;
        private String userName;
        private LocalDateTime createdAt;
        
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getOpenId() { return openId; }
        public void setOpenId(String openId) { this.openId = openId; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
    
    /**
     * 声纹注册提交结果。
     *
     * @param success   是否成功
     * @param message   提示信息
     * @param featureId 讯飞特征 ID（失败时为 {@code null}）
     */
    public static class RegisterResult {
        private final boolean success;
        private final String message;
        private final String featureId;
        
        public RegisterResult(boolean success, String message, String featureId) {
            this.success = success;
            this.message = message;
            this.featureId = featureId;
        }
        
        /** @return 是否注册成功 */
        public boolean isSuccess() { return success; }
        /** @return 用户可见说明 */
        public String getMessage() { return message; }
        /** @return 特征 ID */
        public String getFeatureId() { return featureId; }
    }
    
    /**
     * 用户声纹有效性摘要。
     *
     * @param isValid    是否在有效期内
     * @param isExpiring 是否 30 天内到期
     * @param expiresAt  过期时间（未注册时为 {@code null}）
     */
    public static class VoiceprintStatus {
        private final boolean isValid;
        private final boolean isExpiring;
        private final LocalDateTime expiresAt;
        
        public VoiceprintStatus(boolean isValid, boolean isExpiring, LocalDateTime expiresAt) {
            this.isValid = isValid;
            this.isExpiring = isExpiring;
            this.expiresAt = expiresAt;
        }
        
        /** @return 声纹是否在有效期内 */
        public boolean isValid() { return isValid; }
        /** @return 是否临近过期（30 天内） */
        public boolean isExpiring() { return isExpiring; }
        /** @return 过期时间 */
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }
}