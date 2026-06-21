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
 * 会中多维表格展示排序：
 * 近三个月在前（按创建日期）；近三月内状态子段展示序：延期 → 已完成 → 进行中；
 * 「进行中」子段内：截止日期升序（最早优先，无截止日期排最后）；
 * 同段内：近 7 日已完成 → 未完成 → 进行中 → 更早已完成。
 */
public final class BitableRecordSorter {

    /** 同时间组内：近 7 日内已完成（创建日期或截止日期落在 7 天内） */
    public static final int TIER_COMPLETED = 0;
    /** 同时间组内：未完成等待办 */
    public static final int TIER_INCOMPLETE = 1;
    /** 同时间组内：进行中 */
    public static final int TIER_IN_PROGRESS = 2;
    /** 同时间组内：已完成但开始/截止日期均不在近 7 日 */
    public static final int TIER_STALE_COMPLETED = 3;

    /** 会中展示：已完成 */
    public static final int CATEGORY_COMPLETED = 0;
    /** 会中展示：已延期（未办结且已过截止） */
    public static final int CATEGORY_DELAYED = 1;
    /** 会中展示：进行中（含未延期待办） */
    public static final int CATEGORY_IN_PROGRESS = 2;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long THREE_MONTHS_MS = 92L * 24 * 60 * 60 * 1000;
    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final Pattern DAYS_IN_TEXT = Pattern.compile("(-?\\d+)\\s*天");

    private BitableRecordSorter() {
    }

    public static List<JsonNode> sortedCopy(List<JsonNode> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<JsonNode> copy = new ArrayList<>(items);
        if (copy.size() >= 2) {
            copy.sort(COMPARATOR);
        }
        return copy;
    }

    public static void sort(List<JsonNode> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        List<JsonNode> copy = sortedCopy(items);
        items.clear();
        items.addAll(copy);
    }

    private static final Comparator<JsonNode> COMPARATOR = (a, b) -> {
        int c = Integer.compare(recentBucket(a), recentBucket(b));
        if (c != 0) {
            return c;
        }
        if (recentBucket(a) == 0 && recentBucket(b) == 0) {
            c = Integer.compare(displaySectionOrder(displayCategory(a)), displaySectionOrder(displayCategory(b)));
            if (c != 0) {
                return c;
            }
        }
        if (displayCategory(a) == CATEGORY_IN_PROGRESS && displayCategory(b) == CATEGORY_IN_PROGRESS) {
            c = Long.compare(deadlineSortKeyAsc(a), deadlineSortKeyAsc(b));
            if (c != 0) {
                return c;
            }
        }
        c = Integer.compare(statusTier(a), statusTier(b));
        if (c != 0) {
            return c;
        }
        if (statusTier(a) == TIER_INCOMPLETE || statusTier(a) == TIER_STALE_COMPLETED) {
            c = Long.compare(deadlineSortKeyDesc(b), deadlineSortKeyDesc(a));
            if (c != 0) {
                return c;
            }
        }
        if (statusTier(a) == TIER_COMPLETED) {
            c = Long.compare(recentCompletedAnchorMs(b), recentCompletedAnchorMs(a));
            if (c != 0) {
                return c;
            }
        }
        long createdA = createdTimeMs(a);
        long createdB = createdTimeMs(b);
        return Long.compare(createdB, createdA);
    };

    /**
     * @return 状态分层，含近 7 日已完成与更早已完成。
     */
    static int statusTier(JsonNode record) {
        if (!statusTextIndicatesComplete(resolveStatusText(record))
                && !Boolean.TRUE.equals(resolveCompletionFlag(record))) {
            String statusText = resolveStatusText(record);
            if (statusText != null && statusTextIndicatesInProgress(statusText)) {
                return TIER_IN_PROGRESS;
            }
            return TIER_INCOMPLETE;
        }
        return isCompletedWithinSevenDays(record) ? TIER_COMPLETED : TIER_STALE_COMPLETED;
    }

    static boolean isCompleted(JsonNode record) {
        return statusTextIndicatesComplete(resolveStatusText(record))
                || Boolean.TRUE.equals(resolveCompletionFlag(record));
    }

    static boolean isDelayed(JsonNode record) {
        if (isCompleted(record)) {
            return false;
        }
        Long deadlineMs = extractDeadlineDateMs(record);
        // 业务规则：无截止日期一律不归类为「延期」，统一落到「进行中」分类。
        if (deadlineMs == null || deadlineMs <= 0) {
            return false;
        }
        String status = resolveStatusText(record);
        if (status != null) {
            String s = status.trim();
            if (s.contains("延期") || s.contains("逾期") || s.contains("超期") || s.contains("过期")) {
                return true;
            }
        }
        Long days = extractDaysToDeadline(record);
        if (days != null && days < 0) {
            return true;
        }
        String daysFieldText = resolveDaysToDeadlineText(record);
        if (daysFieldText != null) {
            String t = daysFieldText.trim();
            if (t.contains("已延期") || t.contains("逾期") || t.contains("超期") || t.contains("过期")) {
                return true;
            }
        }
        return deadlineMs != null && deadlineMs < System.currentTimeMillis() - 86_400_000L;
    }

    /**
     * 近三个月状态子段在 plainText / 主持页中的展示顺序（与 {@code CATEGORY_*} 语义 ID 独立）。
     *
     * @return 0=延期, 1=已完成, 2=进行中
     */
    static int displaySectionOrder(int category) {
        return switch (category) {
            case CATEGORY_DELAYED -> 0;
            case CATEGORY_COMPLETED -> 1;
            case CATEGORY_IN_PROGRESS -> 2;
            default -> 2;
        };
    }

    /**
     * 近三个月区块内的展示分类（语义 ID，非展示顺序）。
     */
    public static int displayCategory(JsonNode record) {
        if (isCompleted(record)) {
            return CATEGORY_COMPLETED;
        }
        if (isDelayed(record)) {
            return CATEGORY_DELAYED;
        }
        return CATEGORY_IN_PROGRESS;
    }

    private static String resolveDaysToDeadlineText(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (isDaysToDeadlineFieldName(e.getKey())) {
                return BitableFieldFormatter.format(e.getValue(), e.getKey());
            }
        }
        return null;
    }

    /** 近 7 日已完成：创建日期或截止日期任一落在最近 7 天内。 */
    static boolean isCompletedWithinSevenDays(JsonNode record) {
        long anchor = recentCompletedAnchorMs(record);
        if (anchor <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        return anchor >= now - SEVEN_DAYS_MS && anchor <= now + 86_400_000L;
    }

    /** 取创建日期/截止日期中较新者，用于 7 日窗口与排序。 */
    static long recentCompletedAnchorMs(JsonNode record) {
        Long creation = extractCreationDateMs(record);
        Long deadline = extractDeadlineDateMs(record);
        if (creation != null && deadline != null) {
            return Math.max(creation, deadline);
        }
        if (creation != null) {
            return creation;
        }
        return deadline != null ? deadline : 0L;
    }

    private static String resolveStatusText(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (isStatusFieldName(e.getKey())) {
                return BitableFieldFormatter.format(e.getValue(), e.getKey());
            }
        }
        return null;
    }

    private static Boolean resolveCompletionFlag(JsonNode record) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!isCompletionFlagFieldName(e.getKey())) {
                continue;
            }
            JsonNode val = e.getValue();
            if (val.isBoolean()) {
                return val.asBoolean();
            }
            String text = BitableFieldFormatter.format(val, e.getKey());
            if ("是".equals(text) || "true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("否".equals(text) || "false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return null;
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

    /** 截止日期升序；优先「距离截止」天数，其次截止日期毫秒；无值排最后。 */
    static long deadlineSortKeyAsc(JsonNode record) {
        Long days = extractDaysToDeadline(record);
        if (days != null) {
            return days;
        }
        Long deadlineMs = extractDeadlineDateMs(record);
        return deadlineMs != null ? deadlineMs : Long.MAX_VALUE;
    }

    /**
     * @return 0=近三个月内，1=更早或无业务「创建日期/创建时间」
     * <p>仅按字段创建日期判断，不使用飞书 API {@code created_time}，避免与表内「开始日期」等混淆。</p>
     */
    public static int recentBucket(JsonNode record) {
        Long fromField = extractCreationDateMs(record);
        if (fromField == null || fromField <= 0) {
            return 1;
        }
        long age = System.currentTimeMillis() - fromField;
        return age > THREE_MONTHS_MS ? 1 : 0;
    }

    static long createdTimeMs(JsonNode record) {
        Long fromField = extractCreationDateMs(record);
        if (fromField != null && fromField > 0) {
            return fromField;
        }
        long top = parseEpochMillis(record.path("created_time"));
        if (top > 0) {
            return top;
        }
        top = parseEpochMillis(record.path("created_at"));
        return Math.max(top, 0L);
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
        return extractDateMsByField(record, BitableRecordSorter::isDeadlineDateFieldName);
    }

    private static Long extractCreationDateMs(JsonNode record) {
        return extractDateMsByField(record, BitableRecordSorter::isCreationDateFieldName);
    }

    private interface FieldNamePredicate {
        boolean test(String name);
    }

    private static Long extractDateMsByField(JsonNode record, FieldNamePredicate matcher) {
        JsonNode fields = record.path("fields");
        if (!fields.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!matcher.test(e.getKey())) {
                continue;
            }
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
        if (text.contains("已延期")) {
            Matcher overdue = DAYS_IN_TEXT.matcher(text);
            if (overdue.find()) {
                try {
                    return -Math.abs(Long.parseLong(overdue.group(1)));
                } catch (NumberFormatException ignored) {
                    return -1L;
                }
            }
            return -1L;
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

    private static boolean isCreationDateFieldName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("创建日期") || name.contains("创建时间")
                || name.equalsIgnoreCase("created_time") || name.equalsIgnoreCase("created_at");
    }

    static boolean statusTextIndicatesInProgress(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        return s.contains("进行中") || s.contains("in progress") || s.contains("in_progress");
    }

    static boolean statusTextIndicatesComplete(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        if (statusTextIndicatesInProgress(status)) {
            return false;
        }
        if (s.contains("未完成") || s.contains("待办") || s.contains("待开始")
                || s.contains("未开始") || s.contains("pending")) {
            return false;
        }
        return s.contains("已完成") || s.contains("已关闭") || s.contains("已办结")
                || s.contains("closed") || s.contains("done")
                || (s.contains("完成") && !s.contains("未完成"))
                || s.equals("是") || "true".equals(s);
    }
}
