package com.smartmeeting.enums;

/**
 * 音频来源类型。
 */
public enum AudioSourceType {
    /** 浏览器麦克风 / 主持页 WebSocket 录音 */
    MICROPHONE,
    /** 系统立体声混音 / 回环声卡 */
    STEREO_MIX,
    /** 手工上传 */
    UPLOAD,
    /** 云端 URL 下载 */
    CLOUD,
    /** 未知或未上报 */
    UNKNOWN
}
