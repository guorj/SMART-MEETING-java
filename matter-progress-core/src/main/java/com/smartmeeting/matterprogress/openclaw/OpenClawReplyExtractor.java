package com.smartmeeting.matterprogress.openclaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从 OpenClaw Gateway 各类响应（WS chat 事件 / HTTP JSON）中提取助手正文。
 */
public final class OpenClawReplyExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenClawReplyExtractor() {
    }

    public static String extractFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            JsonNode json = MAPPER.readTree(trimmed);
            String extracted = extractFromJson(json);
            if (extracted != null && !extracted.isBlank()) {
                return extracted.trim();
            }
            return null;
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    public static String extractFromJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            String t = node.asText();
            return t.isBlank() ? null : t;
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                String part = extractFromJson(item);
                if (part != null && !part.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(part.trim());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        if (!node.isObject()) {
            return null;
        }

        if (node.has("status") && "ok".equals(node.path("status").asText())) {
            JsonNode payloads = node.path("result").path("payloads");
            if (payloads.isArray() && !payloads.isEmpty()) {
                String fromPayloads = extractFromJson(payloads);
                if (fromPayloads != null && !fromPayloads.isBlank()) {
                    return fromPayloads;
                }
            }
        }

        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String fromChoices = extractFromJson(choices.get(0).path("message"));
            if (fromChoices != null && !fromChoices.isBlank()) {
                return fromChoices;
            }
        }

        for (String field : new String[]{
                "reply", "content", "message", "response", "text", "deltaText", "output", "result"
        }) {
            if (!node.has(field) || node.get(field).isNull()) {
                continue;
            }
            String part = extractFromJson(node.get(field));
            if (part != null && !part.isBlank()) {
                return part;
            }
        }

        JsonNode data = node.path("data");
        if (data.isObject()) {
            String fromData = extractFromJson(data);
            if (fromData != null && !fromData.isBlank()) {
                return fromData;
            }
        }
        return null;
    }
}
