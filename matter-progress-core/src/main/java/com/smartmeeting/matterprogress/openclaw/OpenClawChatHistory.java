package com.smartmeeting.matterprogress.openclaw;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 解析 OpenClaw Gateway {@code chat.history} 返回的 session transcript。
 */
public final class OpenClawChatHistory {

    private OpenClawChatHistory() {
    }

    public static int countAssistantMessages(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode m : messages) {
            if (m != null && "assistant".equals(m.path("role").asText(""))) {
                count++;
            }
        }
        return count;
    }

    public static String extractLatestAssistantText(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode m = messages.get(i);
            if (m == null || !"assistant".equals(m.path("role").asText(""))) {
                continue;
            }
            String text = OpenClawReplyExtractor.extractFromJson(m.path("content"));
            if (text == null || text.isBlank()) {
                text = OpenClawReplyExtractor.extractFromJson(m);
            }
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    /**
     * 取 {@code chat.send} 之后新增的 assistant 正文，避免 transcript 未落盘时误用上一条回复。
     */
    public static String extractNewAssistantText(JsonNode messages, int baselineAssistantCount) {
        if (messages == null || !messages.isArray()) {
            return null;
        }
        if (countAssistantMessages(messages) <= baselineAssistantCount) {
            return null;
        }
        return extractLatestAssistantText(messages);
    }
}
