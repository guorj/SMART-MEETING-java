package com.smartmeeting.enums;

/**
 * 会议音频资产角色。
 */
public enum AudioAssetRole {
    /** 原始录音（浏览器 PCM、混音、云端下载等） */
    ORIGINAL,
    /** 标准化后 16k mono s16le，供离线 ASR / ISV 使用 */
    NORMALIZED
}
