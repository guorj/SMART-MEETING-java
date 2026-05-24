package com.smartmeeting.service.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 会序 OpenClaw 通报正文校验：拒绝 Agent 缓存话术、纪要增强 JSON 等非 Markdown 通报。
 */
public final class AgendaBriefingMarkdownValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MIN_LENGTH_WITHOUT_HEADER = 80;

    private AgendaBriefingMarkdownValidator() {
    }

    public static boolean isValid(String markdown) {
        return rejectReason(markdown) == null;
    }

    /**
     * @return 拒绝原因代码；{@code null} 表示可接受
     */
    public static String rejectReason(String text) {
        if (text == null || text.isBlank()) {
            return "empty";
        }
        String t = text.trim();

        if (looksLikeMinuteEnhancementJson(t)) {
            return "minute_enhancement_json";
        }

        if (containsCacheMeta(t) && !hasMarkdownStructure(t)) {
            return "agent_cache_meta";
        }

        if (t.length() < MIN_LENGTH_WITHOUT_HEADER && !hasMarkdownStructure(t)) {
            return "too_short";
        }

        return null;
    }

    private static boolean containsCacheMeta(String t) {
        return t.contains("直接使用缓存")
                || t.contains("已有缓存数据")
                || t.contains("使用缓存数据")
                || (t.contains("/skill:matter-progress") && t.contains("缓存"));
    }

    private static boolean hasMarkdownStructure(String t) {
        return t.contains("# ") || t.startsWith("#");
    }

    private static boolean looksLikeMinuteEnhancementJson(String t) {
        if (t.contains("optimized_minute") || t.contains("quality_check") || t.contains("missing_info")) {
            return true;
        }
        if (!t.startsWith("{")) {
            return false;
        }
        try {
            JsonNode node = MAPPER.readTree(t);
            return node.isObject()
                    && (node.has("optimized_minute") || node.has("quality_check") || node.has("missing_info"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
