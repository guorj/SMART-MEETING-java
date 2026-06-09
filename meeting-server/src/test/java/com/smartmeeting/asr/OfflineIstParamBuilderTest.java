package com.smartmeeting.asr;

import com.smartmeeting.config.MeetingAsrProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineIstParamBuilderTest {

    @Test
    @DisplayName("auto 模式：≥2 声纹 → roleType=3 + featureIds")
    void auto_withFeatures_usesVoiceprint() {
        MeetingAsrProperties props = baseProps();
        props.setOfflineRoleMode("auto");

        OfflineIstOptions options = OfflineIstParamBuilder.resolve(
                props, List.of("f1", "f2"), 8);

        assertThat(options.roleType()).isEqualTo(3);
        assertThat(options.roleNum()).isEqualTo(8);
        assertThat(options.featureIdsCsv()).isEqualTo("f1,f2");
    }

    @Test
    @DisplayName("auto 模式：声纹不足 → roleType=1 盲分")
    void auto_insufficientFeatures_blind() {
        MeetingAsrProperties props = baseProps();
        props.setOfflineRoleMode("auto");

        OfflineIstOptions options = OfflineIstParamBuilder.resolve(
                props, List.of("f1"), 5);

        assertThat(options.roleType()).isEqualTo(1);
        assertThat(options.roleNum()).isEqualTo(5);
        assertThat(options.featureIdsCsv()).isNull();
    }

    @Test
    @DisplayName("blind 模式始终 roleType=1")
    void blind_alwaysRoleType1() {
        MeetingAsrProperties props = baseProps();
        props.setOfflineRoleMode("blind");

        OfflineIstOptions options = OfflineIstParamBuilder.resolve(
                props, List.of("f1", "f2"), 8);

        assertThat(options.roleType()).isEqualTo(1);
        assertThat(options.featureIdsCsv()).isNull();
    }

    @Test
    @DisplayName("offline-role-enabled=false → roleType=0")
    void roleDisabled() {
        MeetingAsrProperties props = baseProps();
        props.setOfflineRoleEnabled(false);

        OfflineIstOptions options = OfflineIstParamBuilder.resolve(
                props, List.of("f1", "f2"), 8);

        assertThat(options.roleType()).isEqualTo(0);
    }

    @Test
    @DisplayName("buildUploadParams 在 roleType=3 时包含 featureIds 与 roleNum")
    void buildUploadParams_voiceprint() {
        OfflineIstOptions options = OfflineIstOptions.voiceprint(8, "a,b");
        Map<String, String> params = XfyunOfflineClient.buildUploadParams(
                "app", "key", "2026-01-01T00:00:00+0800", "rand123456789012",
                1000, "m.pcm", 5000, options);

        assertThat(params.get("roleType")).isEqualTo("3");
        assertThat(params.get("roleNum")).isEqualTo("8");
        assertThat(params.get("featureIds")).isEqualTo("a,b");
    }

    private static MeetingAsrProperties baseProps() {
        MeetingAsrProperties props = new MeetingAsrProperties();
        props.setOfflineRoleEnabled(true);
        props.setOfflineRoleNumHintEnabled(true);
        return props;
    }
}
