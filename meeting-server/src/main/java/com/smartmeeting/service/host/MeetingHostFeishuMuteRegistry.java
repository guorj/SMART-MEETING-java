package com.smartmeeting.service.host;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 会议主持人会中「零飞书」：对群 chat_id 静音，拦截发群消息/卡片。
 */
@Component
public class MeetingHostFeishuMuteRegistry {

    private final Set<String> mutedChatIds = ConcurrentHashMap.newKeySet();

    public void muteChat(String chatId) {
        if (chatId != null && !chatId.isBlank()) {
            mutedChatIds.add(chatId);
        }
    }

    public void unmuteChat(String chatId) {
        if (chatId != null && !chatId.isBlank()) {
            mutedChatIds.remove(chatId);
        }
    }

    public boolean isMuted(String chatId) {
        return chatId != null && mutedChatIds.contains(chatId);
    }
}
