package com.smartmeeting.util;

import com.smartmeeting.entity.Meeting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingPresetTypeCodesTest {

    @Test
    void templateClassification() {
        Meeting m = new Meeting();
        m.setPresetTypeCode(1);
        assertThat(MeetingPresetTypeCodes.isTemplateMeeting(m.getPresetTypeCode())).isTrue();
        assertThat(MeetingPresetTypeCodes.isCustomMeeting(m.getPresetTypeCode())).isFalse();

        m.setPresetTypeCode(6);
        assertThat(MeetingPresetTypeCodes.isTemplateMeeting(m.getPresetTypeCode())).isTrue();
        assertThat(MeetingPresetTypeCodes.isCustomMeeting(m.getPresetTypeCode())).isFalse();
    }
}
