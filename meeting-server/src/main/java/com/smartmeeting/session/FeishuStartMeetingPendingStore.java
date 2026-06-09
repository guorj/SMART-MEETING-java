package com.smartmeeting.session;

import com.smartmeeting.config.MeetingSessionProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书「开始会议」多步会话状态存储（进程内）。
 * <p>
 * 跟踪菜单后选类型/填主题，或「开始会议 6」后仅填主题等中间等待状态。
 */
@Component
public class FeishuStartMeetingPendingStore {

    /**
     * 多步会话等待类型。
     */
    public enum Kind {
        /** 已展示类型菜单，等待单字 1-5 或任意自定义主题 */
        POST_MENU_CHOICE,
        /** 已选「开始会议 6」，等待下一行主题 */
        TYPE6_THEME_PENDING
    }

    private final long ttlMs;

    private static final class Entry {
        final Kind kind;
        final long expiresAtMs;

        Entry(Kind kind, long expiresAtMs) {
            this.kind = kind;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public FeishuStartMeetingPendingStore(MeetingSessionProperties sessionProperties) {
        this.ttlMs = sessionProperties.getFeishuStartPendingTtlMinutes() * 60L * 1000L;
    }

    private static String key(String openId, String chatId) {
        return openId + "|" + chatId;
    }

    public void markPostMenuChoice(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        entries.put(key(openId, chatId), new Entry(Kind.POST_MENU_CHOICE, System.currentTimeMillis() + ttlMs));
    }

    public void markTypeSixThemePending(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        entries.put(key(openId, chatId), new Entry(Kind.TYPE6_THEME_PENDING, System.currentTimeMillis() + ttlMs));
    }

    public Kind getKind(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return null;
        }
        String k = key(openId, chatId);
        Entry e = entries.get(k);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expiresAtMs) {
            entries.remove(k);
            return null;
        }
        return e.kind;
    }

    public void clear(String openId, String chatId) {
        if (openId == null || chatId == null) {
            return;
        }
        entries.remove(key(openId, chatId));
    }
}
