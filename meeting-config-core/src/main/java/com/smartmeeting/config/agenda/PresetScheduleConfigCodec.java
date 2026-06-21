package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link PresetScheduleConfig} JSON 编解码与 scheduled_time 解析。
 */
public final class PresetScheduleConfigCodec {

    private static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private PresetScheduleConfigCodec() {
    }

    public static Optional<PresetScheduleConfig> parse(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (root.isNull() || !root.isObject()) {
                return Optional.empty();
            }
            PresetScheduleConfig cfg = new PresetScheduleConfig();
            cfg.setType(text(root, "type"));
            if (root.has("weekday") && !root.get("weekday").isNull()) {
                cfg.setWeekday(root.get("weekday").asInt());
            }
            if (root.has("hour") && !root.get("hour").isNull()) {
                cfg.setHour(root.get("hour").asInt());
            }
            if (root.has("minute") && !root.get("minute").isNull()) {
                cfg.setMinute(root.get("minute").asInt());
            }
            if (root.has("preferNextIfPast") && !root.get("preferNextIfPast").isNull()) {
                cfg.setPreferNextIfPast(root.get("preferNextIfPast").asBoolean());
            }
            String atRaw = text(root, "at");
            if (atRaw != null && !atRaw.isBlank()) {
                cfg.setAt(parseLocalDateTime(atRaw.trim()));
            }
            if (cfg.getType() == null || cfg.getType().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(cfg);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static String toJson(ObjectMapper mapper, PresetScheduleConfig config) {
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(config);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> validate(ObjectMapper mapper, String json) {
        List<String> issues = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return issues;
        }
        Optional<PresetScheduleConfig> parsed = parse(mapper, json);
        if (parsed.isEmpty()) {
            issues.add("schedule_config 不是有效的 JSON 对象");
            return issues;
        }
        return validate(parsed.get());
    }

    public static List<String> validate(PresetScheduleConfig config) {
        List<String> issues = new ArrayList<>();
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            return issues;
        }
        String type = config.getType().trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case "at_start" -> { /* no extra fields */ }
            case "fixed" -> {
                if (config.getAt() == null) {
                    issues.add("fixed 类型须填写 at");
                }
            }
            case "weekly" -> {
                if (config.getWeekday() == null || config.getWeekday() < 1 || config.getWeekday() > 7) {
                    issues.add("weekly 类型 weekday 须为 1–7");
                }
                if (config.getHour() == null || config.getHour() < 0 || config.getHour() > 23) {
                    issues.add("weekly 类型 hour 须为 0–23");
                }
                if (config.getMinute() == null || config.getMinute() < 0 || config.getMinute() > 59) {
                    issues.add("weekly 类型 minute 须为 0–59");
                }
            }
            default -> issues.add("schedule_config.type 须为 weekly、fixed 或 at_start");
        }
        return issues;
    }

    /**
     * 将预设排期规则解析为具体 {@code scheduled_time}。
     *
     * @param referenceTime 参考时刻（instant-start 一般为 now）
     */
    public static LocalDateTime resolve(PresetScheduleConfig config, LocalDateTime referenceTime) {
        LocalDateTime ref = referenceTime != null ? referenceTime : LocalDateTime.now();
        if (config == null || config.getType() == null || config.getType().isBlank()) {
            return ref;
        }
        String type = config.getType().trim().toLowerCase(Locale.ROOT);
        if ("at_start".equals(type)) {
            return ref;
        }
        if ("fixed".equals(type)) {
            return config.getAt() != null ? config.getAt() : ref;
        }
        if ("weekly".equals(type)) {
            int weekday = config.getWeekday() != null ? config.getWeekday() : 1;
            int hour = config.getHour() != null ? config.getHour() : 9;
            int minute = config.getMinute() != null ? config.getMinute() : 0;
            DayOfWeek target = DayOfWeek.of(Math.min(7, Math.max(1, weekday)));
            LocalDate refDate = ref.toLocalDate();
            LocalDate slotDate = refDate.with(TemporalAdjusters.previousOrSame(target));
            LocalDateTime candidate = LocalDateTime.of(slotDate, LocalTime.of(hour, minute));
            boolean preferNext = config.getPreferNextIfPast() == null || config.getPreferNextIfPast();
            if (preferNext && candidate.isBefore(ref)) {
                candidate = candidate.plusWeeks(1);
            }
            return candidate;
        }
        return ref;
    }

    public static LocalDateTime resolve(ObjectMapper mapper, String json, LocalDateTime referenceTime) {
        return resolve(parse(mapper, json).orElse(null), referenceTime);
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.asText();
    }

    private static LocalDateTime parseLocalDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw, ISO_LOCAL);
        } catch (DateTimeParseException e) {
            if (raw.length() >= 16 && raw.charAt(10) == ' ') {
                return LocalDateTime.parse(raw.replace(' ', 'T'), ISO_LOCAL);
            }
            throw e;
        }
    }
}
