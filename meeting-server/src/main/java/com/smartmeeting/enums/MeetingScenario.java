package com.smartmeeting.enums;

/**
 * 会议场景枚举：用于区分三类音频链路策略。
 */
public enum MeetingScenario {
    /** 纯线下：默认本地录音 + 本地离线校正。 */
    OFFLINE,
    /** 线上线下混合：优先本地录音，可配置云端录音兜底。 */
    HYBRID,
    /** 纯线上：优先云端录音下载后离线处理。 */
    ONLINE;

    /**
     * 宽松解析字符串；空或非法值回退到 OFFLINE。
     */
    public static MeetingScenario fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFFLINE;
        }
        try {
            return MeetingScenario.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return OFFLINE;
        }
    }
}
