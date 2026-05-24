package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会中多维表格展示排序：近三个月创建在前 → 未完成在前 → 未完成按距离截止日降序。
 */
public final class BitableRecordSorter {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long THREE_MONTHS_MS = 92L * 24 * 60 * 60 * 1000;
    private static final Pattern DAYS_IN_TEXT = Pattern.compile("(-?\\d+)\\s*天");

    private BitableRecordSorter() {
    }

    public static void sort(List<JsonNode> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        List<JsonNode> copy = new ArrayList<>(items);
        copy.sort(COMPARATOR);
        items.clear();
        items.addAll(copy);
    }

    private static final Comparator<JsonNode> COMPARATOR = (a, b) -> {
        int c = Integer.compare(recentBucket(a), recentBucket(b));
        if (c != 0) {
            return c;
        }
        c = Boolean.compare(isCompleted(a), isCompleted(b));
        if (c != 0) {
            return c;
        }
        if (!isCompleted(a)) {
            c = Long.compare(deadlineSortKeyDesc(b), deadlineSortKeyDesc(a));
            if (c != 0) {
                return c;
            }
        }
        long createdA = createdTimeMs(a);
        long createdB = createdTimeMs(b);
        return Long.compare(createdB, createdA);
    };

    /** @return 0=未完成在前，1=已完成在后 */
    static boolean isCompleted(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            JsonNode val = e.getValue();
            if (isStatusFieldName(name)) {
                return statusTextIndicatesComplete(BitableFieldFormatter.format(val, name));
            }
            if (isCompletionFlagFieldName(name)) {
                if (val.isBoolean()) {
                    return val.asBoolean();
                }
                String text = BitableFieldFormatter.format(val, name);
                if ("是".equals(text) || "true".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("否".equals(text) || "false".equalsIgnoreCase(text)) {
                    return false;
                }
            }
        }
        return false;
    }

    /** 距离截止日/截止日期等，降序；无值排最后。 */
    static long deadlineSortKeyDesc(JsonNode record) {
        Long days = extractDaysToDeadline(record);
        if (days != null) {
            return days;
        }
        Long deadlineMs = extractDeadlineDateMs(record);
        return deadlineMs != null ? deadlineMs : Long.MIN_VALUE;
    }

    /** @return 0=近三个月内，1=更早 */
    static int recentBucket(JsonNode record) {
        long created = createdTimeMs(record);
        if (created <= 0) {
            return 0;
        }
        long age = System.currentTimeMillis() - created;
        return age > THREE_MONTHS_MS ? 1 : 0;
    }

    static long createdTimeMs(JsonNode record) {
        long top = parseEpochMillis(record.path("created_time"));
        if (top > 0) {
            return top;
        }
        top = parseEpochMillis(record.path("created_at"));
        if (top > 0) {
            return top;
        }
        JsonNode fields = record.path("fields");
        if (fields.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (isCreatedTimeFieldName(e.getKey())) {
                    long ms = parseEpochMillis(e.getValue());
                    if (ms > 0) {
                        return ms;
                    }
                    String formatted = BitableFieldFormatter.format(e.getValue(), e.getKey());
                    ms = parseEpochMillisFromText(formatted);
                    if (ms > 0) {
                        return ms;
                    }
                }
            }
        }
        return 0L;
    }

    private static Long extractDaysToDeadline(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!isDaysToDeadlineFieldName(e.getKey())) {
                continue;
            }
            Long fromNode = parseDaysFromNode(e.getValue());
            if (fromNode != null) {
                return fromNode;
            }
            String text = BitableFieldFormatter.format(e.getValue(), e.getKey());
            Long fromText = parseDaysFromText(text);
            if (fromText != null) {
                return fromText;
            }
        }
        return null;
    }

    private static Long extractDeadlineDateMs(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!isDeadlineDateFieldName(e.getKey())) {
                continue;
            }
            long ms = parseEpochMillis(e.getValue());
            if (ms > 0) {
                return ms;
            }
        }
        return null;
    }

    private static Long parseDaysFromNode(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            return parseDaysFromText(value.asText());
        }
        return parseDaysFromText(BitableFieldFormatter.format(value, null));
    }

    static Long parseDaysFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.matches("^-?\\d+$")) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher m = DAYS_IN_TEXT.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            long days = Long.parseLong(m.group(1));
            if (text.contains("逾期") || text.contains("超期") || text.contains("过期")) {
                return -Math.abs(days);
            }
            return days;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long parseEpochMillis(JsonNode value) {
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            long n = value.asLong();
            return n < 1_000_000_000_000L ? n * 1000L : n;
        }
        if (value.isTextual()) {
            return parseEpochMillisFromText(value.asText());
        }
        if (value.isObject() && value.has("value")) {
            return parseEpochMillis(value.get("value"));
        }
        return 0L;
    }

    private static long parseEpochMillisFromText(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        String s = text.trim();
        if (s.matches("^-?\\d+$")) {
            try {
                long n = Long.parseLong(s);
                return n < 1_000_000_000_000L ? n * 1000L : n;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static boolean isStatusFieldName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("状态") || name.contains("完成情况") || name.equalsIgnoreCase("status");
    }

    private static boolean isCompletionFlagFieldName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("是否完成") || n.contains("已完成");
    }

    private static boolean isDaysToDeadlineFieldName(String name) {
        return name != null && name.contains("距离截止");
    }

    private static boolean isDeadlineDateFieldName(String name) {
        if (name == null) {
            return false;
        }
        return (name.contains("截止日期") || name.contains("截止时间") || name.equalsIgnoreCase("deadline"))
                && !name.contains("距离");
    }

    private static boolean isCreatedTimeFieldName(String name) {
        return name != null && (name.contains("创建时间") || name.equalsIgnoreCase("created_time"));
    }

    static boolean statusTextIndicatesComplete(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (s.contains("未完成") || s.contains("进行中") || s.contains("待办")
                || s.contains("待开始") || s.contains("未开始") || s.contains("pending")
                || s.contains("in progress") || s.contains("in_progress")) {
            return false;
        }
        return s.contains("已完成") || s.contains("完成") || s.contains("已关闭")
                || s.contains("已办结") || s.contains("closed") || s.contains("done")
                || s.equals("是") || "true".equals(s);
    }
}
