package com.smartmeeting.session;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书「开始会议」多步会话状态存储（进程内）。
 * <p>
 * 跟踪菜单后选类型/填主题，或「开始会议 6」后仅填主题等中间等待状态，TTL 30 分钟。
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

    private static final long TTL_MS = 30 * 60 * 1000L;

    private static final class Entry {
        final Kind kind;
        final long expiresAtMs;

        Entry(Kind kind, long expiresAtMs) {
            this.kind = kind;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private static String key(String openId, String chatId) {
        return openId + "|" + chatId;
    }

    /**
     * 标记用户已收到类型选择菜单，等待后续输入。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     */
    public void markPostMenuChoice(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        entries.put(key(openId, chatId), new Entry(Kind.POST_MENU_CHOICE, System.currentTimeMillis() + TTL_MS));
    }

    /**
     * 标记用户已选择「开始会议 6」，等待下一行输入自定义主题。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     */
    public void markTypeSixThemePending(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        entries.put(key(openId, chatId), new Entry(Kind.TYPE6_THEME_PENDING, System.currentTimeMillis() + TTL_MS));
    }

    /**
     * 获取当前等待类型。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     * @return 等待类型；null 表示无等待或已过期
     */
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

    /**
     * 清除指定用户与会话的多步等待状态。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 飞书群聊 chat_id
     */
    public void clear(String openId, String chatId) {
        if (openId == null || chatId == null) {
            return;
        }
        entries.remove(key(openId, chatId));
    }
}
