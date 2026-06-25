package com.smartmeeting.enums;

/**
 * 标准化后音频质量状态。
 */
public enum AudioQualityStatus {
    /** 质量正常，可进入 ASR */
    OK,
    /** 音量偏低，默认不送 ASR */
    LOW_VOLUME,
    /** 近似静音 */
    SILENT,
    /** 格式元信息缺失或与文件长度不匹配 */
    FORMAT_UNKNOWN,
    /** 转码/标准化失败 */
    CONVERT_FAILED,
    /** 时长过短 */
    TOO_SHORT
}
