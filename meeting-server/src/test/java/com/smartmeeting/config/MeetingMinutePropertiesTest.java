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

    @Test
    @DisplayName("未配置时 skill-generation-enabled 默认 true")
    void defaultSkillGenerationEnabled() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        assertThat(props.isSkillGenerationEnabled()).isTrue();
    }

    @Test
    @DisplayName("未配置时 llm/feishu-doc/notify 默认 true")
    void defaultStepFlagsEnabled() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        assertThat(props.isLlmEnabled()).isTrue();
        assertThat(props.isFeishuDocEnabled()).isTrue();
        assertThat(props.isNotifyEnabled()).isTrue();
    }

    @Test
    @DisplayName("可显式关闭 llm/feishu-doc/notify")
    void disableStepFlags() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        props.setLlmEnabled(false);
        props.setFeishuDocEnabled(false);
        props.setNotifyEnabled(false);
        assertThat(props.isLlmEnabled()).isFalse();
        assertThat(props.isFeishuDocEnabled()).isFalse();
        assertThat(props.isNotifyEnabled()).isFalse();
    }
}
