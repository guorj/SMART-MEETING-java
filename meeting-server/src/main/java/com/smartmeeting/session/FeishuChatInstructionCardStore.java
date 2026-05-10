package com.smartmeeting.session;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录某群是否已发送过「首次开始会议」常驻说明卡片（进程内；多实例部署需改为 Redis 等）。
 */
@Component
public class FeishuChatInstructionCardStore {

    private final ConcurrentHashMap<String, Boolean> sent = new ConcurrentHashMap<>();

    /**
     * @return true 表示本次为首次（调用方应发说明卡片）；false 表示该群已发过
     */
    public boolean markFirstInstructionIfAbsent(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return false;
        }
        return sent.putIfAbsent(chatId.trim(), Boolean.TRUE) == null;
    }
}
