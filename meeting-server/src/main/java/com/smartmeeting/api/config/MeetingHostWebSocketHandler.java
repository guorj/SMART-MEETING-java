package com.smartmeeting.api.config;

import com.smartmeeting.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * AI 会议主持页 WebSocket：仅 JSON 状态与 TTS 音频分片，路径 {@code /ws/host/{meetingId}?token=}.
 */
@Slf4j
@Component
public class MeetingHostWebSocketHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;

    private final Map<String, Set<WebSocketSession>> sessionsByMeeting = new ConcurrentHashMap<>();

    public MeetingHostWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String meetingId = extractMeetingId(session);
        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }
        try {
            Claims claims = jwtUtil.parseToken(token);
            String tokenMeetingId = claims.get("meetingId", String.class);
            if (tokenMeetingId == null) {
                tokenMeetingId = claims.getSubject();
            }
            if (meetingId == null || !meetingId.equals(tokenMeetingId)) {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Token mismatch"));
                return;
            }
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Token parse error"));
            return;
        }

        sessionsByMeeting.computeIfAbsent(meetingId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("Host WS connected: meetingId={}, sessionId={}", meetingId, session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 主持态以服务端计时为准；客户端可扩展 ACK，当前忽略
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Host WS error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String meetingId = extractMeetingId(session);
        Set<WebSocketSession> set = sessionsByMeeting.get(meetingId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByMeeting.remove(meetingId);
            }
        }
        log.info("Host WS closed: meetingId={}, code={}", meetingId, closeStatus.getCode());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void broadcastText(String meetingId, String json) {
        Set<WebSocketSession> set = sessionsByMeeting.get(meetingId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage tm = new TextMessage(json);
        for (WebSocketSession s : set) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(tm);
                } catch (Exception e) {
                    log.warn("Host WS send failed: {}", e.getMessage());
                }
            }
        }
    }

    private String extractMeetingId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
