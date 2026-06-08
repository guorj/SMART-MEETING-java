package com.smartmeeting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingAsrPropertiesTest {

    @Test
    @DisplayName("未配置时 offline-enabled 默认 true")
    void defaultOfflineEnabled() {
        MeetingAsrProperties props = new MeetingAsrProperties();
        assertThat(props.isOfflineEnabled()).isTrue();
    }

    @Test
    @DisplayName("可显式关闭 offline-enabled")
    void disableOffline() {
        MeetingAsrProperties props = new MeetingAsrProperties();
        props.setOfflineEnabled(false);
        assertThat(props.isOfflineEnabled()).isFalse();
    }
}
