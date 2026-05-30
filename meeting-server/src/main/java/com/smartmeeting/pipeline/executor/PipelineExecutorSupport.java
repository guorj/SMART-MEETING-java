package com.smartmeeting.pipeline.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PipelineExecutorSupport {

    private final ObjectMapper objectMapper;

    public JsonNode parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(configJson);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    public String text(JsonNode node, String field, String defaultValue) {
        String v = node.path(field).asText(defaultValue);
        return v == null ? defaultValue : v;
    }

    public int number(JsonNode node, String field, int defaultValue) {
        JsonNode x = node.path(field);
        if (x.isNumber()) {
            return x.asInt(defaultValue);
        }
        try {
            return Integer.parseInt(x.asText(""));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public boolean bool(JsonNode node, String field, boolean defaultValue) {
        JsonNode x = node.path(field);
        if (x.isBoolean()) {
            return x.asBoolean(defaultValue);
        }
        String raw = x.asText("");
        if (raw.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    public String renderTemplate(String template, StepExecutionContext context, Map<String, String> extra) {
        String out = template == null ? "" : template;
        out = out.replace("{meetingId}", context.getMeetingId() == null ? "" : context.getMeetingId());
        Meeting m = context.getMeeting();
        out = out.replace("{meetingTitle}", m == null || m.getTitle() == null ? "" : m.getTitle());
        out = out.replace("{chatId}", m == null || m.getChatId() == null ? "" : m.getChatId());
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    public String toJson(ObjectNode node) {
        return node == null ? "{}" : node.toString();
    }

    public ObjectNode newObject() {
        return objectMapper.createObjectNode();
    }
}

