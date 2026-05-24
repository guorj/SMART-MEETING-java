package com.smartmeeting.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingMinuteQueryServiceTest {

    @Test
    void preferDocUrl_meetingWinsOverTruncatedMinute() {
        String minute = "https://x.feishu.cn/docx/abc".repeat(20);
        if (minute.length() > 300) {
            minute = minute.substring(0, 300);
        }
        String meeting = "https://x.feishu.cn/docx/abc?full=1";
        assertThat(MeetingMinuteQueryService.preferDocUrl(minute, meeting)).isEqualTo(meeting);
    }

    @Test
    void preferDocUrl_fallsBackToMinuteWhenMeetingEmpty() {
        assertThat(MeetingMinuteQueryService.preferDocUrl("https://x.feishu.cn/docx/a", null))
                .isEqualTo("https://x.feishu.cn/docx/a");
    }
}
