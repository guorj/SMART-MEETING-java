package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;

/**
 * 飞书多维表格字段值 → 主持页可读文本（日期、布尔、公式 type/value 等）。
 */
public final class BitableFieldFormatter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private BitableFieldFormatter() {
    }

    /** 从 bitable API {@code fields} 节点格式化单字段。 */
    public static String format(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "是" : "否";
        }
        if (value.isNumber()) {
            return formatNumber(value.asLong(), fieldName);
        }
        if (value.isTextual()) {
            return formatTextual(value.asText(), fieldName);
        }
        if (value.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : value) {
                String part = format(item, fieldName);
                if (part == null || part.isBlank()) {
                    continue;
                }
                if (!sb.isEmpty()) {
                    sb.append('、');
                }
                sb.append(part);
            }
            return sb.toString();
        }
        if (value.isObject()) {
            JsonNode inner = value.get("value");
            if (inner != null && !inner.isNull()) {
                return format(inner, fieldName);
            }
            if (value.has("text")) {
                return value.path("text").asText("");
            }
            if (value.has("name")) {
                return value.path("name").asText("");
            }
            if (value.has("text_arr") && value.get("text_arr").isArray()) {
                return format(value.get("text_arr"), fieldName);
            }
            Iterator<Map.Entry<String, JsonNode>> it = value.fields();
            if (it.hasNext() && value.size() <= 3) {
                Map.Entry<String, JsonNode> first = it.next();
                if ("link".equals(first.getKey()) || "url".equals(first.getKey())) {
                    return first.getValue().asText("");
                }
            }
        }
        return value.asText("");
    }

    /** 解析已序列化为字符串的字段（兼容旧缓存/plainText）。 */
    public static String formatRawString(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        if ("true".equalsIgnoreCase(s)) {
            return "是";
        }
        if ("false".equalsIgnoreCase(s)) {
            return "否";
        }
        if (s.matches("^-?\\d+$")) {
            try {
                return formatNumber(Long.parseLong(s), fieldName);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        if (s.startsWith("{") || s.startsWith("[")) {
            try {
                JsonNode node = JSON.readTree(s);
                return format(node, fieldName);
            } catch (Exception ignored) {
                return s;
            }
        }
        return s;
    }

    private static String formatTextual(String s, String fieldName) {
        if ("true".equalsIgnoreCase(s)) {
            return "是";
        }
        if ("false".equalsIgnoreCase(s)) {
            return "否";
        }
        if (s.matches("^-?\\d+$")) {
            try {
                return formatNumber(Long.parseLong(s), fieldName);
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        if (s.startsWith("{") || s.startsWith("[")) {
            return formatRawString(s, fieldName);
        }
        return s;
    }

    private static String formatNumber(long n, String fieldName) {
        if (looksLikeEpochMillis(n) && prefersDateField(fieldName)) {
            return formatEpochMillis(n, fieldName);
        }
        return String.valueOf(n);
    }

    static boolean looksLikeEpochMillis(long n) {
        return n >= 946_684_800_000L && n <= 4_102_444_800_000L;
    }

    private static boolean prefersDateField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return true;
        }
        String f = fieldName;
        if (f.contains("日期") || f.contains("时间") || f.contains("Date") || f.contains("date")) {
            return true;
        }
        return false;
    }

    private static String formatEpochMillis(long ms, String fieldName) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZONE);
        boolean dateOnlyField = fieldName != null
                && fieldName.contains("日期")
                && !fieldName.contains("时间");
        if (dateOnlyField || (dt.getHour() == 0 && dt.getMinute() == 0 && dt.getSecond() == 0)) {
            return dt.format(DATE_FMT);
        }
        return dt.format(DATETIME_FMT);
    }
}
