package com.smartmeeting.enums;

/**
 * 参会到场方式：决定会中检点链路。
 *
 * <ul>
 *   <li>{@link #OFFLINE} — AI 主持逐一点名，现场单麦 ASR 答到</li>
 *   <li>{@link #ONLINE} — 个人入会链接打开即登记，禁止向服务器推流</li>
 * </ul>
 */
public enum AttendanceMode {

    /** 线下到场（创建会议时默认值） */
    OFFLINE,

    /** 远程接入，使用个人入会链接完成线上检点 */
    ONLINE;

    /**
     * 从字符串解析到场方式；空或非法值时返回 {@link #OFFLINE}。
     *
     * @param raw 创建会议时 {@code participants[].attendanceMode} 或库表字段值
     * @return 解析后的枚举，永不为 null
     */
    public static AttendanceMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFFLINE;
        }
        try {
            return AttendanceMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFFLINE;
        }
    }
}
