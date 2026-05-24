package com.smartmeeting.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawTaskIdsTest {

    @Test
    @DisplayName("会序通报 taskId 含会议、会序下标与代次")
    void briefingTaskIdFormat() {
        assertThat(OpenClawTaskIds.briefing("m-1", 1, 3)).isEqualTo("briefing:m-1:1:3");
        assertThat(OpenClawTaskIds.isBriefingTask("briefing:m-1:1:3")).isTrue();
        assertThat(OpenClawTaskIds.isMinuteEnhancementTask("briefing:m-1:1:3")).isFalse();
    }

    @Test
    @DisplayName("纪要增强 taskId 与会序隔离")
    void minuteEnhancementTaskIdFormat() {
        String tid = OpenClawTaskIds.minuteEnhancement("m-1", 99L);
        assertThat(tid).startsWith("minute_enhancement:m-1:");
        assertThat(OpenClawTaskIds.isMinuteEnhancementTask(tid)).isTrue();
    }
}
