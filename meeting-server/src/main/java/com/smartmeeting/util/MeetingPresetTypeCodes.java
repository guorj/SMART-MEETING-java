package com.smartmeeting.util;

import com.smartmeeting.entity.Meeting;

/**
 * 会议预设类型编码：与 {@code int_meeting.preset_type_code} 及各子表冗余列一致。
 */
public final class MeetingPresetTypeCodes {

    /** 固定会务模板会（综合管理会等） */
    public static final int TEMPLATE_MIN = 1;
    public static final int TEMPLATE_MAX = 5;
    /** 自定义会（非 1-5 模板） */
    public static final int CUSTOM_OTHER = 6;

    private MeetingPresetTypeCodes() {
    }

    public static Integer fromMeeting(Meeting meeting) {
        return meeting != null ? meeting.getPresetTypeCode() : null;
    }

    public static boolean isTemplateMeeting(Integer code) {
        return code != null && code >= TEMPLATE_MIN && code <= TEMPLATE_MAX;
    }

    public static boolean isCustomMeeting(Integer code) {
        return code != null && code == CUSTOM_OTHER;
    }
}
