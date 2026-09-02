package com.smartmeeting.service;

import com.smartmeeting.config.MeetingVcProperties;
import com.smartmeeting.entity.Meeting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VcRecordingPostMeetingPolicyTest {

    private MeetingVcProperties vcProperties;
    private VcRecordingPostMeetingPolicy policy;

    @BeforeEach
    void setUp() {
        vcProperties = new MeetingVcProperties();
        vcProperties.setRecordingEnabled(true);
        policy = new VcRecordingPostMeetingPolicy(vcProperties);
    }

    @Test
    void expectsVcRecording_whenUrlPresentAndEnabled() {
        Meeting meeting = new Meeting();
        meeting.setVcMeetingUrl("https://vc.feishu.cn/j/1");
        assertThat(policy.expectsVcRecording(meeting)).isTrue();
    }

    @Test
    void expectsVcRecording_falseWhenNoUrl() {
        assertThat(policy.expectsVcRecording(new Meeting())).isFalse();
    }

    @Test
    void isAwaitingVcToken_whenUrlWithoutToken() {
        Meeting meeting = new Meeting();
        meeting.setVcMeetingUrl("https://vc.feishu.cn/j/1");
        assertThat(policy.isAwaitingVcToken(meeting)).isTrue();
    }

    @Test
    void isPastCallbackTimeout_afterDeadline() {
        vcProperties.setCallbackTimeoutMin(15);
        Meeting meeting = new Meeting();
        meeting.setActualEndTime(LocalDateTime.now().minusMinutes(20));
        assertThat(policy.isPastCallbackTimeout(meeting)).isTrue();
    }
}
