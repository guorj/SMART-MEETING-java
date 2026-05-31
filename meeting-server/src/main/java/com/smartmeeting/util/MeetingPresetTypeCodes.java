package com.smartmeeting.util;

import com.smartmeeting.entity.Meeting;

/**
 * 会议预设类型编码：与 {@code int_meeting.preset_type_code} 及各子表冗余列一致。
 */
public final class MeetingPresetTypeCodes {

    /** 模板会：任意正整数 preset_type_code。 */
    public static final int TEMPLATE_MIN = 1;

    private MeetingPresetTypeCodes() {
    }

    public static Integer fromMeeting(Meeting meeting) {
        return meeting != null ? meeting.getPresetTypeCode() : null;
    }

    public static boolean isTemplateMeeting(Integer code) {
        return code != null && code >= TEMPLATE_MIN;
    }

    public static boolean isCustomMeeting(Integer code) {
        return false;
    }
}
