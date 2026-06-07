package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.asr.XfyunIsvClient;
import com.smartmeeting.asr.XfyunIsvClient.VoiceprintMember;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.entity.TranscriptSegment;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TranscriptMapper;
import com.smartmeeting.repository.VoiceprintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞 ISV 声纹识别与转写说话人标注服务。
 *
 * <p>能力：注册声纹入库、实时片段识别、批量回写转写段说话人姓名、Redis 特征缓存与过期清理。
 *
 * <p>主要协作：{@link XfyunIsvClient}、{@link VoiceprintMapper}、
 * {@link ParticipantMapper}、{@link TranscriptMapper}、可选 {@link RedisTemplate}。
 */
@Slf4j
@Service
public class VoiceprintService {

    private final XfyunIsvClient isvClient;
    private final VoiceprintMapper voiceprintMapper;
    private final ParticipantMapper participantMapper;
    private final TranscriptMapper transcriptMapper;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * @param isvClient           讯飞 ISV 客户端
     * @param voiceprintMapper    声纹持久化
     * @param participantMapper   参会人
     * @param transcriptMapper    转写分段
     * @param redisTemplate       可选 Redis 缓存（未注入时跳过缓存）
     */
    public VoiceprintService(XfyunIsvClient isvClient, VoiceprintMapper voiceprintMapper,
                             ParticipantMapper participantMapper, TranscriptMapper transcriptMapper,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) RedisTemplate<String, String> redisTemplate) {
        this.isvClient = isvClient;
        this.voiceprintMapper = voiceprintMapper;
        this.participantMapper = participantMapper;
        this.transcriptMapper = transcriptMapper;
        this.redisTemplate = redisTemplate;
    }

    private static final String CACHE_KEY_PREFIX = "voiceprint:feature:";
    private static final long CACHE_EXPIRE_HOURS = 72;

    /**
     * 注册用户声纹
     *
     * @param userId 用户标识（OA userId）
     * @param userName 用户姓名
     * @param audioSample 音频样本（PCM格式，建议5-10秒）
     * @return 声纹特征 ID；ISV 或入库失败时返回 {@code null}
     */
    @Transactional
    public String registerVoiceprint(String userId, String userName, byte[] audioSample) {
        log.info("Registering voiceprint: userId={}, userName={}, audioLen={}bytes", userId, userName, audioSample.length);

        // 调用讯飞ISV注册
        String featureId = isvClient.registerVoiceprint(userId, userName, audioSample);
        if (featureId == null) {
            log.error("ISV register failed for userId: {}", userId);
            return null;
        }

        // 写入数据库
        Voiceprint voiceprint = new Voiceprint();
        voiceprint.setId(UUID.randomUUID().toString());
        voiceprint.setUserId(Integer.parseInt(userId));
        voiceprint.setUserName(userName);
        voiceprint.setFeishuUserId(null); // 可后续补充
        voiceprint.setFeatureId(featureId);
        voiceprint.setGroupId(isvClient.getGroupId());
        voiceprint.setRegisteredAt(LocalDateTime.now());
        voiceprint.setExpiresAt(LocalDateTime.now().plusDays(90));

        voiceprintMapper.insert(voiceprint);

        // 缓存到 Redis（如果可用）
        if (redisTemplate != null) {
            String cacheKey = CACHE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(cacheKey, featureId, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        log.info("Voiceprint registered and saved: userId={}, featureId={}", userId, featureId);
        return featureId;
    }

    /**
     * 识别音频片段说话人
     *
     * @param meetingId 会议ID
     * @param audioSegment 音频片段（PCM格式，建议3-5秒）
     * @return 说话人姓名；无法识别时返回 {@code speaker_unknown} 或 {@code speaker_<featureId前缀>}
     */
    /**
     * ISV 1:N 识别，返回 featureId 或 speaker_unknown。
     */
    public String identifyFeatureId(byte[] audioSegment) {
        if (audioSegment == null || audioSegment.length == 0) {
            return "speaker_unknown";
        }
        String featureId = isvClient.identifyVoiceprint(audioSegment);
        if (featureId == null || featureId.isBlank() || "speaker_unknown".equals(featureId)) {
            return "speaker_unknown";
        }
        return featureId;
    }

    /**
     * 将 featureId 解析为展示姓名（参会人优先，其次声纹库）。
     */
    public String resolveDisplayName(String featureId, String meetingId) {
        if (featureId == null || featureId.isBlank() || "speaker_unknown".equals(featureId)) {
            return null;
        }
        Participant participant = participantMapper.selectOne(
                new LambdaQueryWrapper<Participant>()
                        .eq(Participant::getMeetingId, meetingId)
                        .eq(Participant::getFeatureId, featureId)
                        .last("LIMIT 1"));
        if (participant != null && participant.getName() != null && !participant.getName().isBlank()) {
            return participant.getName();
        }
        Voiceprint vp = voiceprintMapper.selectOne(
                new LambdaQueryWrapper<Voiceprint>()
                        .eq(Voiceprint::getFeatureId, featureId)
                        .last("LIMIT 1"));
        if (vp != null && vp.getUserName() != null && !vp.getUserName().isBlank()) {
            if (participant == null) {
                log.warn("Voiceprint matched outside meeting participants: meetingId={}, featureId={}, name={}",
                        meetingId, featureId, vp.getUserName());
            }
            return vp.getUserName();
        }
        return null;
    }

    public String identifySpeaker(String meetingId, byte[] audioSegment) {
        log.debug("Identifying speaker for meeting: {}, audioLen={}bytes", meetingId, audioSegment.length);
        String featureId = identifyFeatureId(audioSegment);
        if ("speaker_unknown".equals(featureId)) {
            return "speaker_unknown";
        }
        String name = resolveDisplayName(featureId, meetingId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        log.warn("Speaker identified but name not found: featureId={}", featureId);
        return "speaker_" + featureId.substring(0, Math.min(8, featureId.length()));
    }

    /**
     * 批量更新会议转录段的说话人姓名
     *
     * @param meetingId 会议 ID
     * @return 实际更新条数
     */
    @Transactional
    public int updateTranscriptSpeakers(String meetingId) {
        log.info("Updating transcript speakers for meeting: {}", meetingId);

        // 查询会议所有转录段
        List<TranscriptSegment> segments = transcriptMapper.selectList(
                new LambdaQueryWrapper<TranscriptSegment>()
                        .eq(TranscriptSegment::getMeetingId, meetingId));

        // 获取会议参会人信息（featureId → name 映射）
        List<Participant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<Participant>()
                        .eq(Participant::getMeetingId, meetingId));

        Map<String, String> featureIdToName = new HashMap<>();
        for (Participant p : participants) {
            if (p.getFeatureId() != null && !p.getFeatureId().isEmpty()) {
                featureIdToName.put(p.getFeatureId(), p.getName());
            }
        }

        // 更新每个段的 speakerName
        int updatedCount = 0;
        for (TranscriptSegment segment : segments) {
            String speakerId = segment.getSpeakerId();
            if (speakerId == null || speakerId.isEmpty()) {
                continue;
            }

            String speakerName = null;

            // 如果 speakerId 是 "speaker_N" 格式，需要识别
            if (speakerId.startsWith("speaker_") && !speakerId.equals("speaker_unknown")) {
                // 尝试从音频片段识别（如果有）
                // 实际场景：离线校正时会传入音频路径
                // 这里简化处理：尝试从缓存匹配
                speakerName = matchSpeakerFromCache(speakerId, featureIdToName);
            }

            // 如果 speakerId 是 featureId 格式
            if (featureIdToName.containsKey(speakerId)) {
                speakerName = featureIdToName.get(speakerId);
            }

            if (speakerName != null && !speakerName.equals(segment.getSpeakerName())) {
                segment.setSpeakerName(speakerName);
                transcriptMapper.updateById(segment);
                updatedCount++;
                log.debug("Updated segment speaker: id={}, speakerId={}, speakerName={}",
                        segment.getId(), speakerId, speakerName);
            }
        }

        log.info("Updated {} transcript segments for meeting {}", updatedCount, meetingId);
        return updatedCount;
    }

    /**
     * 根据 speakerId 前缀与 featureId 映射表尝试匹配真实姓名。
     *
     * @param speakerId         转写中的说话人占位 ID
     * @param featureIdToName   会议参会人 featureId → 姓名
     * @return 匹配到的姓名；无匹配时返回 {@code null}
     */
    private String matchSpeakerFromCache(String speakerId, Map<String, String> featureIdToName) {
        // 尝试解析 speakerId 是否包含 featureId 前缀
        for (Map.Entry<String, String> entry : featureIdToName.entrySet()) {
            String featureId = entry.getKey();
            if (speakerId.contains(featureId.substring(0, 8))) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 检查 OA 用户声纹是否已注册且未过期（先 Redis 后 DB）。
     *
     * @param userId OA 用户 ID 字符串
     * @return 有效声纹存在时为 {@code true}
     */
    public boolean isVoiceprintRegistered(String userId) {
        // 先查缓存（如果Redis可用）
        String cacheKey = CACHE_KEY_PREFIX + userId;
        if (redisTemplate != null) {
            String cachedFeatureId = redisTemplate.opsForValue().get(cacheKey);
            if (cachedFeatureId != null) {
                return true;
            }
        }

        // 查数据库
        Voiceprint vp = voiceprintMapper.selectOne(
                new LambdaQueryWrapper<Voiceprint>()
                        .eq(Voiceprint::getUserId, Integer.parseInt(userId))
                        .gt(Voiceprint::getExpiresAt, LocalDateTime.now()));

        if (vp != null) {
            // 回写缓存
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(cacheKey, vp.getFeatureId(), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }
            return true;
        }

        return false;
    }

    /**
     * 从 ISV 声纹组列表全量刷新 Redis 特征缓存。
     */
    public void refreshVoiceprintCache() {
        List<VoiceprintMember> members = isvClient.listVoiceprintGroup();

        for (VoiceprintMember member : members) {
            String cacheKey = CACHE_KEY_PREFIX + member.userId;
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(cacheKey, member.featureId, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            }
        }

        log.info("Voiceprint cache refreshed from ISV: {} entries", members.size());
    }

    /**
     * 删除数据库中已过期声纹，并同步从 ISV 与 Redis 移除。
     *
     * @return 删除条数
     */
    @Transactional
    public int cleanExpiredVoiceprints() {
        List<Voiceprint> expired = voiceprintMapper.selectList(
                new LambdaQueryWrapper<Voiceprint>()
                        .lt(Voiceprint::getExpiresAt, LocalDateTime.now()));

        int deleted = 0;
        for (Voiceprint vp : expired) {
            // 从ISV删除
            isvClient.deleteVoiceprint(vp.getFeatureId());
            // 从数据库删除
            voiceprintMapper.deleteById(vp.getId());
            // 清理缓存
            if (redisTemplate != null) {
                String cacheKey = CACHE_KEY_PREFIX + vp.getUserId();
                redisTemplate.delete(cacheKey);
            }
            deleted++;
        }

        log.info("Cleaned {} expired voiceprints", deleted);
        return deleted;
    }

    /**
     * 获取会议参会人的声纹特征ID列表
     *
     * @param meetingId 会议 ID
     * @return 已标记声纹就绪参会人的 featureId 列表
     */
    public List<String> getMeetingFeatureIds(String meetingId) {
        List<Participant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<Participant>()
                        .eq(Participant::getMeetingId, meetingId)
                        .eq(Participant::getVoiceprintReady, true));

        List<String> featureIds = new ArrayList<>();
        for (Participant p : participants) {
            if (p.getFeatureId() != null && !p.getFeatureId().isEmpty()) {
                featureIds.add(p.getFeatureId());
            }
        }

        log.debug("Meeting {} has {} voiceprint-ready participants", meetingId, featureIds.size());
        return featureIds;
    }
}