package com.smartmeeting.service.host;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 会议主持人会中飞书群静音注册表（零飞书对外干扰策略）。
 *
 * <p>会中将对指定群 {@code chat_id} 标记为静音，供消息/卡片发送链路拦截，
 * 避免主持流程向飞书群推送通知。与 {@link MeetingHostSessionService} 启停主持配合使用。
 */
@Component
public class MeetingHostFeishuMuteRegistry {

    private final Set<String> mutedChatIds = ConcurrentHashMap.newKeySet();

    /**
     * 将飞书群加入静音集合。
     *
     * @param chatId 飞书群 chat_id，null 或空白时忽略
     */
    public void muteChat(String chatId) {
        if (chatId != null && !chatId.isBlank()) {
            mutedChatIds.add(chatId);
        }
    }

    /**
     * 将飞书群移出静音集合。
     *
     * @param chatId 飞书群 chat_id，null 或空白时忽略
     */
    public void unmuteChat(String chatId) {
        if (chatId != null && !chatId.isBlank()) {
            mutedChatIds.remove(chatId);
        }
    }

    /**
     * 查询群是否处于会中静音状态。
     *
     * @param chatId 飞书群 chat_id
     * @return chatId 非空且已 mute 时为 true
     */
    public boolean isMuted(String chatId) {
        return chatId != null && mutedChatIds.contains(chatId);
    }
}
