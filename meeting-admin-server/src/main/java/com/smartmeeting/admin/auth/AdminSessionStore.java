package com.smartmeeting.admin.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminSessionStore {

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    public String createSession(String feishuUserId, long ttlHours) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new SessionEntry(feishuUserId, Instant.now().plusSeconds(ttlHours * 3600)));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        SessionEntry e = sessions.get(token);
        if (e == null) {
            return false;
        }
        if (Instant.now().isAfter(e.expiresAt())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public Optional<String> userId(String token) {
        SessionEntry e = sessions.get(token);
        return e == null ? Optional.empty() : Optional.ofNullable(e.feishuUserId());
    }

    private record SessionEntry(String feishuUserId, Instant expiresAt) {
    }
}
