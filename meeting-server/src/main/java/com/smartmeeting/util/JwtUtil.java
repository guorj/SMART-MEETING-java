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

@Component
public class JwtUtil {

    public static final String CLAIM_PURPOSE = "purpose";
    public static final String PURPOSE_FEISHU_WEB_START_MEETING = "feishu_web_start_meeting";
    public static final String CLAIM_OPEN_ID = "open_id";
    public static final String CLAIM_CHAT_ID = "chat_id";

    @Value("${meeting.jwt.secret:change-me-in-production}")
    private String secret;

    @Value("${meeting.jwt.expire-hours:4}")
    private int expireHours;

    @Value("${meeting.jwt.feishu-web-entry-expire-minutes:30}")
    private int feishuWebEntryExpireMinutes;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

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

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 飞书内打开 Web 选会页时的短期 JWT（仅声明 open_id / chat_id，防伪造创建会议）。
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

    public record FeishuWebStartMeetingEntry(String openId, String chatId) {}

    /**
     * 校验录音页 URL 中的 JWT：须匹配 path 中的会议 ID（subject 或 meetingId claim）。
     */
    public void verifyRecordingPageToken(String token, String meetingId) {
        final Claims c;
        try {
            c = parseToken(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(401, "录音链接已过期，请从飞书重新开会或在后台重新调取录音链接");
        } catch (MalformedJwtException | SignatureException e) {
            throw new BusinessException(401, "录音链接无效（token 损坏或密钥已变更），请重新获取录音链接");
        } catch (JwtException e) {
            throw new BusinessException(401, "录音链接校验失败，请从飞书重新打开录音页");
        } catch (Exception e) {
            throw new BusinessException(401, "录音链接已失效，请从飞书重新打开录音页");
        }
        String sub = c.getSubject();
        String mid = c.get("meetingId", String.class);
        if (!meetingId.equals(sub) && !meetingId.equals(mid)) {
            throw new BusinessException(403, "令牌与会议不匹配");
        }
        String typ = c.get("type", String.class);
        if (typ != null && !"recording".equals(typ) && !"host".equals(typ)) {
            throw new BusinessException(403, "无效的会议页面令牌类型");
        }
    }
}
