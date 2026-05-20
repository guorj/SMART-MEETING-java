package com.smartmeeting.util;

import com.smartmeeting.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 会议 JWT 令牌工具类。
 * <p>
 * 负责生成与校验各类会议访问令牌：飞书 Web 入口、录音/主持操作员、线上个人入会等。
 */
@Component
public class JwtUtil {

    /** JWT claim 键：令牌用途标识 */
    public static final String CLAIM_PURPOSE = "purpose";
    /** 飞书 Web 选会页入口令牌用途值 */
    public static final String PURPOSE_FEISHU_WEB_START_MEETING = "feishu_web_start_meeting";
    /** JWT claim 键：飞书 open_id */
    public static final String CLAIM_OPEN_ID = "open_id";
    /** JWT claim 键：飞书 chat_id */
    public static final String CLAIM_CHAT_ID = "chat_id";
    /** JWT claim 键：会议 ID */
    public static final String CLAIM_MEETING_ID = "meetingId";
    /** JWT claim 键：页面类型（recording/host/join） */
    public static final String CLAIM_TYPE = "type";
    /** 页面类型：录音页 */
    public static final String TYPE_RECORDING = "recording";
    /** 页面类型：主持页 */
    public static final String TYPE_HOST = "host";
    /** 页面类型：个人入会页 */
    public static final String TYPE_JOIN = "join";
    /** JWT claim 键：参会人用户 ID */
    public static final String CLAIM_USER_ID = "user_id";
    /** JWT claim 键：参会人展示姓名 */
    public static final String CLAIM_DISPLAY_NAME = "display_name";
    /** JWT claim 键：到场方式（OFFLINE/ONLINE） */
    public static final String CLAIM_ATTENDANCE_MODE = "attendance_mode";
    /** JWT claim 键：是否允许向 /ws/audio 推流 */
    public static final String CLAIM_CAN_PUSH_AUDIO = "can_push_audio";

    @Value("${meeting.jwt.secret:change-me-in-production}")
    private String secret;

    @Value("${meeting.jwt.expire-hours:4}")
    private int expireHours;

    @Value("${meeting.jwt.feishu-web-entry-expire-minutes:30}")
    private int feishuWebEntryExpireMinutes;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成通用 JWT 令牌。
     *
     * @param subject JWT subject（通常为会议 ID 或业务标识）
     * @param claims  附加声明键值对
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并验证 JWT 令牌。
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims 载荷
     * @throws JwtException 签名无效、过期或格式错误时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 静默校验 JWT 是否有效（不抛异常）。
     *
     * @param token JWT 字符串
     * @return 有效返回 true，否则 false
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成飞书内打开 Web 选会页时的短期 JWT（仅声明 open_id / chat_id，防伪造创建会议）。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     * @return 短期有效的入口 JWT
     */
    public String generateFeishuWebStartMeetingEntryToken(String openId, String chatId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_PURPOSE, PURPOSE_FEISHU_WEB_START_MEETING);
        claims.put(CLAIM_OPEN_ID, openId != null ? openId : "");
        claims.put(CLAIM_CHAT_ID, chatId != null ? chatId : "");
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (long) feishuWebEntryExpireMinutes * 60_000L);
        return Jwts.builder()
                .subject("feishu-web-start-meeting")
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并验证飞书 Web 选会入口 JWT。
     *
     * @param token 入口 JWT 字符串
     * @return 包含 openId 与 chatId 的解析结果
     * @throws BusinessException 令牌无效、过期或缺少必要声明时抛出（HTTP 401）
     */
    public FeishuWebStartMeetingEntry parseAndVerifyFeishuWebStartMeetingEntry(String token) {
        final Claims c;
        try {
            c = parseToken(token);
        } catch (Exception e) {
            throw new BusinessException(401, "飞书入口链接已失效或无效，请返回会话重新点「开始会议」");
        }
        if (!PURPOSE_FEISHU_WEB_START_MEETING.equals(c.get(CLAIM_PURPOSE))) {
            throw new BusinessException(401, "飞书入口链接已失效或无效，请返回会话重新点「开始会议」");
        }
        String oid = c.get(CLAIM_OPEN_ID, String.class);
        String cid = c.get(CLAIM_CHAT_ID, String.class);
        if (oid == null || oid.isBlank() || cid == null || cid.isBlank()) {
            throw new BusinessException(401, "飞书入口链接已失效或无效，请返回会话重新点「开始会议」");
        }
        return new FeishuWebStartMeetingEntry(oid, cid);
    }

    /**
     * 飞书 Web 选会入口 JWT 解析结果。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     */
    public record FeishuWebStartMeetingEntry(String openId, String chatId) {}

    /**
     * 生成会议室操作员令牌：可推流录音/主持（混合会场线下单麦）。
     *
     * @param meetingId 会议 ID
     * @param pageType  页面类型，{@link #TYPE_RECORDING} 或 {@link #TYPE_HOST}，null 时默认 host
     * @return 操作员 JWT
     */
    public String generateOperatorMeetingToken(String meetingId, String pageType) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_MEETING_ID, meetingId);
        claims.put(CLAIM_TYPE, pageType != null ? pageType : TYPE_HOST);
        claims.put(CLAIM_CAN_PUSH_AUDIO, true);
        claims.put(CLAIM_ATTENDANCE_MODE, "OFFLINE");
        return generateToken(meetingId, claims);
    }

    /**
     * 生成线上参会人个人入会链接 JWT：仅盘点，禁止向 /ws/audio 推 PCM。
     *
     * @param meetingId   会议 ID
     * @param userId      参会人用户 ID
     * @param displayName 参会人展示姓名
     * @return 个人入会 JWT
     */
    public String generateParticipantJoinToken(String meetingId, String userId, String displayName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_MEETING_ID, meetingId);
        claims.put(CLAIM_TYPE, TYPE_JOIN);
        claims.put(CLAIM_USER_ID, userId != null ? userId : "");
        claims.put(CLAIM_DISPLAY_NAME, displayName != null ? displayName : "");
        claims.put(CLAIM_ATTENDANCE_MODE, "ONLINE");
        claims.put(CLAIM_CAN_PUSH_AUDIO, false);
        return generateToken(meetingId, claims);
    }

    /**
     * 会议页 JWT 解析结果（录音/主持/个人入会通用）。
     *
     * @param meetingId      会议 ID
     * @param userId         参会人用户 ID（操作员令牌可能为 null）
     * @param displayName    参会人展示姓名
     * @param attendanceMode 到场方式 OFFLINE/ONLINE
     * @param canPushAudio   是否允许推流
     * @param pageType       页面类型 recording/host/join
     */
    public record ParticipantMeetingToken(
            String meetingId,
            String userId,
            String displayName,
            String attendanceMode,
            boolean canPushAudio,
            String pageType
    ) {
    }

    /**
     * 解析会议页 JWT（录音/主持/个人入会）。
     *
     * @param token             JWT 字符串
     * @param expectedMeetingId 期望的会议 ID，null 时不校验
     * @return 解析后的令牌信息
     * @throws BusinessException 令牌无效或与会议不匹配时抛出
     */
    public ParticipantMeetingToken parseParticipantMeetingToken(String token, String expectedMeetingId) {
        Claims c = parseTokenOrThrow(token);
        String mid = c.get(CLAIM_MEETING_ID, String.class);
        String sub = c.getSubject();
        if (expectedMeetingId != null && !expectedMeetingId.equals(mid) && !expectedMeetingId.equals(sub)) {
            throw new BusinessException(403, "令牌与会议不匹配");
        }
        String resolvedMeetingId = mid != null && !mid.isBlank() ? mid : sub;
        Boolean canPush = c.get(CLAIM_CAN_PUSH_AUDIO, Boolean.class);
        return new ParticipantMeetingToken(
                resolvedMeetingId,
                c.get(CLAIM_USER_ID, String.class),
                c.get(CLAIM_DISPLAY_NAME, String.class),
                c.get(CLAIM_ATTENDANCE_MODE, String.class),
                canPush == null || canPush,
                c.get(CLAIM_TYPE, String.class)
        );
    }

    /**
     * 校验任意有效会议页令牌（含个人入会链接，只读场景）。
     *
     * @param token     JWT 字符串
     * @param meetingId 期望的会议 ID
     * @throws BusinessException 校验失败时抛出
     */
    public void verifyRecordingPageToken(String token, String meetingId) {
        parseParticipantMeetingToken(token, meetingId);
    }

    /**
     * 校验主持/录音操作员令牌：须为操作员令牌且允许推流。
     *
     * @param token     JWT 字符串
     * @param meetingId 期望的会议 ID
     * @throws BusinessException 个人入会链接或无推流权限时抛出（HTTP 403）
     */
    public void verifyHostOperatorToken(String token, String meetingId) {
        ParticipantMeetingToken t = parseParticipantMeetingToken(token, meetingId);
        if (TYPE_JOIN.equals(t.pageType())) {
            throw new BusinessException(403, "个人入会链接仅用于到场确认，不能操作主持或录音");
        }
        if (!t.canPushAudio()) {
            throw new BusinessException(403, "当前链接不允许启动会议室录音");
        }
    }

    /**
     * 校验并解析个人入会令牌。
     *
     * @param token     JWT 字符串
     * @param meetingId 期望的会议 ID
     * @return 解析后的令牌信息
     * @throws BusinessException 非 join 类型令牌时抛出（HTTP 403）
     */
    public ParticipantMeetingToken verifyJoinToken(String token, String meetingId) {
        ParticipantMeetingToken t = parseParticipantMeetingToken(token, meetingId);
        if (!TYPE_JOIN.equals(t.pageType())) {
            throw new BusinessException(403, "请使用个人入会链接完成线上到场确认");
        }
        return t;
    }

    /**
     * 从 JWT 判断当前连接是否允许推流。
     *
     * @param token     JWT 字符串
     * @param meetingId 期望的会议 ID
     * @return 允许推流返回 true
     */
    public boolean canPushAudioFromToken(String token, String meetingId) {
        return parseParticipantMeetingToken(token, meetingId).canPushAudio();
    }

    private Claims parseTokenOrThrow(String token) {
        try {
            return parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "会议链接已过期，请从飞书重新获取");
        } catch (MalformedJwtException | SignatureException e) {
            throw new BusinessException(401, "会议链接无效");
        } catch (JwtException e) {
            throw new BusinessException(401, "会议链接校验失败");
        } catch (Exception e) {
            throw new BusinessException(401, "会议链接已失效");
        }
    }
}
