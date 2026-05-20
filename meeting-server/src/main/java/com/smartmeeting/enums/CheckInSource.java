package com.smartmeeting.enums;

/**
 * 参会人到场（检点）来源，持久化于 {@code int_meeting_participant.check_in_source}。
 *
 * <p>与主持端 {@code host_state.rollCall.people[].checkInSource} 字段语义一致。
 */
public enum CheckInSource {

    /** 线上参会人打开个人入会链接后自动登记 */
    AUTO_ONLINE,

    /** 线下检点阶段经 ASR 识别为答到类语音 */
    ROLL_CALL,

    /** 人工补录或后台修正（预留） */
    MANUAL,

    /** 线上盘点或线下点名窗口超时未应答 */
    TIMEOUT
}
