package com.smartmeeting.session;

import com.smartmeeting.config.MeetingSessionProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书用户最近活跃群聊会话存储（进程内）。
 */
@Component
public class FeishuUserLastGroupChatStore {

    private final long ttlMs;

    private static final class Entry {
        final String chatId;
        final long updatedAtMs;

        Entry(String chatId, long updatedAtMs) {
            this.chatId = chatId;
            this.updatedAtMs = updatedAtMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> byOpenId = new ConcurrentHashMap<>();

    public FeishuUserLastGroupChatStore(MeetingSessionProperties sessionProperties) {
        this.ttlMs = sessionProperties.getFeishuUserLastGroupChatTtlHours() * 60L * 60 * 1000L;
    }

    public void record(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        byOpenId.put(openId.trim(), new Entry(chatId.trim(), System.currentTimeMillis()));
    }

    public String getLastChatId(String openId) {
        if (openId == null || openId.isBlank()) {
            return "";
        }
        Entry e = byOpenId.get(openId.trim());
        if (e == null) {
            return "";
        }
        if (System.currentTimeMillis() - e.updatedAtMs > ttlMs) {
            byOpenId.remove(openId.trim(), e);
            return "";
        }
        return e.chatId;
    }
}
