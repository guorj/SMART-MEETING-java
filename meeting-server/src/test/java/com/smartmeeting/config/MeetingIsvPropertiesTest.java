package com.smartmeeting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingIsvPropertiesTest {

    @Test
    @DisplayName("默认 match-score-threshold 为 0.6")
    void defaultThreshold() {
        MeetingIsvProperties props = new MeetingIsvProperties();
        assertThat(props.getMatchScoreThreshold()).isEqualTo(0.6);
    }
}
