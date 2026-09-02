package com.smartmeeting.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawTaskIdsTest {

    @Test
    @DisplayName("纪要增强 taskId 含会议 ID 与 nonce")
    void minuteEnhancementTaskIdFormat() {
        String tid = OpenClawTaskIds.minuteEnhancement("m-1", 42L);
        assertThat(tid).isEqualTo("minute_enhancement:m-1:42");
        assertThat(OpenClawTaskIds.isMinuteEnhancementTask(tid)).isTrue();
        assertThat(OpenClawTaskIds.isMinuteGenerationTask(tid)).isFalse();
    }

    @Test
    @DisplayName("纪要 Skill 生成 taskId 与增强隔离")
    void minuteGenerationTaskIdFormat() {
        String tid = OpenClawTaskIds.minuteGeneration("m-1", 42L);
        assertThat(tid).startsWith("minute_generation:m-1:");
        assertThat(OpenClawTaskIds.isMinuteGenerationTask(tid)).isTrue();
        assertThat(OpenClawTaskIds.isMinuteEnhancementTask(tid)).isFalse();
    }
}
