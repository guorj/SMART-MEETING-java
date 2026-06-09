package com.smartmeeting.config.system;

/**
 * 运行时数值钳制，替代 scattered {@code Math.max/min} 硬编码。
 */
public final class ConfigValueClamp {

    private ConfigValueClamp() {
    }

    public static int clampInt(int value, int min, int max) {
        if (min > max) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static long clampLong(long value, long min, long max) {
        if (min > max) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static double clampDouble(double value, double min, double max) {
        if (min > max) {
            return value;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** 至少为 {@code floor}，常用于配置项下限保护。 */
    public static int atLeast(int value, int floor) {
        return Math.max(floor, value);
    }

    public static int effectiveTopK(int requested, int min, int max) {
        return clampInt(Math.max(min, requested), min, max);
    }
}
