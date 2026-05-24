package com.smartmeeting.service.agent;

/**
 * meeting-server 对 OpenClaw 回复提取器的薄委托层。
 *
 * <p>核心逻辑已迁至 {@link com.smartmeeting.matterprogress.openclaw.OpenClawReplyExtractor}。
 */
final class OpenClawReplyExtractor {

    private OpenClawReplyExtractor() {
    }

    static String extractFromBody(String body) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawReplyExtractor.extractFromBody(body);
    }

    static String extractFromJson(com.fasterxml.jackson.databind.JsonNode node) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawReplyExtractor.extractFromJson(node);
    }
}
