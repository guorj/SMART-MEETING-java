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
 * AI 会议主持页 WebSocket 处理器。
 * <p>
 * 路径 {@code /ws/host/{meetingId}?token=}，向已连接客户端广播 JSON 主持态与 TTS 音频分片；
 * 主持计时以服务端为准，客户端消息当前忽略。
 *
 * @see JwtUtil
 */
@Slf4j
@Component
public class MeetingHostWebSocketHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;

    /** 会议 ID → 该会议下所有订阅 session（支持多标签页） */
    private final Map<String, Set<WebSocketSession>> sessionsByMeeting = new ConcurrentHashMap<>();

    /**
     * @param jwtUtil JWT 校验
     */
    public MeetingHostWebSocketHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 连接建立：校验 JWT 中 meetingId 与路径一致后注册 session。
     *
     * @param session WebSocket 会话
     * @throws Exception 关闭连接时可能抛出
     */
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

    /**
     * 忽略客户端入站消息（主持态由服务端驱动）。
     *
     * @param session WebSocket 会话
     * @param message 入站消息
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 主持态以服务端计时为准；客户端可扩展 ACK，当前忽略
    }

    /**
     * 传输层错误回调。
     *
     * @param session   WebSocket 会话
     * @param exception 异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Host WS error: {}", exception.getMessage());
    }

    /**
     * 连接关闭：从会议订阅集合中移除 session。
     *
     * @param session     WebSocket 会话
     * @param closeStatus 关闭状态
     */
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

    /**
     * 不支持分片消息。
     *
     * @return 固定为 false
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 向指定会议的所有已连接客户端广播 JSON 文本。
     *
     * @param meetingId 会议 ID
     * @param json      已序列化的 JSON 字符串
     */
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

    /**
     * 从 URI 路径解析会议 ID（{@code /ws/host/{meetingId}}）。
     *
     * @param session WebSocket 会话
     * @return 会议 ID
     */
    private String extractMeetingId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    /**
     * 从查询参数解析 {@code token=} JWT。
     *
     * @param session WebSocket 会话
     * @return token 字符串
     */
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
