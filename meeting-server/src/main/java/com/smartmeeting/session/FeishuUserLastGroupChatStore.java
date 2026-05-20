package com.smartmeeting.session;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书用户最近活跃群聊会话存储（进程内）。
 * <p>
 * 记录用户最近活跃的会话（群或单聊），用于「机器人菜单 - 推送事件」场景：
 * {@code application.bot.menu_v6} 事件体往往不带 {@code chat_id}，可用此映射把操作落到该会话。
 * <p>
 * 单机内存、带 TTL（48 小时）；多实例部署时需改为 Redis 等共享存储。
 */
@Component
public class FeishuUserLastGroupChatStore {

    private static final long TTL_MS = 48L * 60 * 60 * 1000;

    private static final class Entry {
        final String chatId;
        final long updatedAtMs;

        Entry(String chatId, long updatedAtMs) {
            this.chatId = chatId;
            this.updatedAtMs = updatedAtMs;
        }
    }

    private final ConcurrentHashMap<String, Entry> byOpenId = new ConcurrentHashMap<>();

    /**
     * 记录用户最近活跃的群聊 ID。
     *
     * @param openId 飞书用户 open_id
     * @param chatId 群聊或单聊 chat_id
     */
    public void record(String openId, String chatId) {
        if (openId == null || openId.isBlank() || chatId == null || chatId.isBlank()) {
            return;
        }
        byOpenId.put(openId.trim(), new Entry(chatId.trim(), System.currentTimeMillis()));
    }

    /**
     * 获取用户最近活跃的群聊 ID。
     *
     * @param openId 飞书用户 open_id
     * @return 最近 chat_id；无记录或已过期（超过 48 小时）时返回空字符串
     */
    public String getLastChatId(String openId) {
        if (openId == null || openId.isBlank()) {
            return "";
        }
        Entry e = byOpenId.get(openId.trim());
        if (e == null) {
            return "";
        }
        if (System.currentTimeMillis() - e.updatedAtMs > TTL_MS) {
            byOpenId.remove(openId.trim(), e);
            return "";
        }
        return e.chatId;
    }
}
