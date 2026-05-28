package com.smartmeeting.statemachine;

/**
 * 会议状态机事件。
 */
public enum MeetingEvent {
    INVITE_PARTICIPANTS,
    FAST_START,
    START_MEETING,
    START_RECORDING,
    PAUSE_RECORDING,
    RESUME_RECORDING,
    END_MEETING,
    MINUTE_READY,
    TODO_SYNCED,
    ALL_TODOS_DONE,
    ARCHIVE,
    CLOSE_MEETING,
    CANCEL_MEETING,
    ABORT_MEETING
}
