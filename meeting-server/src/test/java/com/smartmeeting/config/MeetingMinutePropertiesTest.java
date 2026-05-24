package com.smartmeeting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingMinutePropertiesTest {

    @Test
    @DisplayName("未配置时 ai-enhancement-enabled 默认 true")
    void defaultAiEnhancementEnabled() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        assertThat(props.isAiEnhancementEnabled()).isTrue();
    }

    @Test
    @DisplayName("可显式关闭 ai-enhancement-enabled")
    void disableAiEnhancement() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        props.setAiEnhancementEnabled(false);
        assertThat(props.isAiEnhancementEnabled()).isFalse();
    }
}
