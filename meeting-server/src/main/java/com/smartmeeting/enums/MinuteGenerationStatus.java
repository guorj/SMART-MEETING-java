package com.smartmeeting.enums;

/**
 * 库内纪要生成状态（与飞书 doc 写入结果可组合）。
 * <p>
 * 持久化于 {@code int_meeting_minute.generation_status}。
 */
public enum MinuteGenerationStatus {
    /** 纪要已完整生成并就绪 */
    READY,
    /** 部分内容生成成功，存在缺失或飞书写入不完整 */
    PARTIAL,
    /** 纪要生成失败 */
    FAILED
}
