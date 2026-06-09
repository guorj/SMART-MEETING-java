package com.smartmeeting.config.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Predicate;

/**
 * Admin {@link SystemConfigDescriptor#validator()} 共用的 JSON 值范围校验。
 */
public final class ConfigJsonValidators {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigJsonValidators() {
    }

    public static Predicate<String> intInRange(int min, int max) {
        return json -> {
            try {
                JsonNode node = MAPPER.readTree(json);
                int v = node.isNumber() ? node.intValue() : Integer.parseInt(node.asText().trim());
                return v >= min && v <= max;
            } catch (Exception e) {
                return false;
            }
        };
    }

    public static Predicate<String> doubleInRange(double min, double max) {
        return json -> {
            try {
                JsonNode node = MAPPER.readTree(json);
                double v = node.isNumber() ? node.doubleValue() : Double.parseDouble(node.asText().trim());
                return v >= min && v <= max;
            } catch (Exception e) {
                return false;
            }
        };
    }

    public static String formatIntRange(int min, int max) {
        return min + "–" + max;
    }

    public static String formatDoubleRange(double min, double max) {
        return min + "–" + max;
    }
}
