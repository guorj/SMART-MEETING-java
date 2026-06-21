package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * PUT 请求中 scheduleConfig 可为 JSON 字符串或对象，统一落库为字符串。
 */
public class ScheduleConfigJsonDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            String text = node.asText();
            return text == null || text.isBlank() ? null : text.trim();
        }
        if (node.isObject()) {
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            return mapper.writeValueAsString(node);
        }
        return null;
    }
}
