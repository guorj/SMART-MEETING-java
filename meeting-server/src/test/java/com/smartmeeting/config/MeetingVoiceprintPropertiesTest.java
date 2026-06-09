package com.smartmeeting.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingVoiceprintPropertiesTest {

    @Test
    @DisplayName("balanced 离线标注默认开关")
    void offlineLabelDefaults() {
        MeetingVoiceprintProperties props = new MeetingVoiceprintProperties();
        assertThat(props.getOfflineVoteSlices()).isEqualTo(3);
        assertThat(props.isOfflineSegmentRelabelEnabled()).isTrue();
        assertThat(props.isOfflineSplitClusterEnabled()).isTrue();
        assertThat(props.getOfflineSplitMinSegments()).isEqualTo(2);
    }
}
