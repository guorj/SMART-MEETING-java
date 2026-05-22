package com.smartmeeting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingMinutePropertiesTest {

    @Test
    @DisplayName("meeting.minute.ai-enhancement-enabled 可绑定")
    void bindAiEnhancementEnabled() {
        var source = new MapConfigurationPropertySource(Map.of(
                "meeting.minute.persist-enabled", "true",
                "meeting.minute.ai-enhancement-enabled", "false"
        ));
        MeetingMinuteProperties props = Binder.get(source)
                .bind("meeting.minute", Bindable.of(MeetingMinuteProperties.class))
                .orElseGet(MeetingMinuteProperties::new);

        assertThat(props.isPersistEnabled()).isTrue();
        assertThat(props.isAiEnhancementEnabled()).isFalse();
    }

    @Test
    @DisplayName("未配置时 ai-enhancement-enabled 默认 true")
    void defaultAiEnhancementEnabled() {
        MeetingMinuteProperties props = new MeetingMinuteProperties();
        assertThat(props.isAiEnhancementEnabled()).isTrue();
    }
}
