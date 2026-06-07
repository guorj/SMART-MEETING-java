package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会后离线声纹标注配置，绑定 {@code meeting.voiceprint.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.voiceprint")
public class MeetingVoiceprintProperties {

    /** 离线转写入库后是否按说话人聚类做 ISV 1:N 并回写姓名。 */
    private boolean offlineLabelEnabled = true;

    /** 代表音频切片最小时长（毫秒）。 */
    private int offlineMinSliceMs = 3000;

    /** 代表音频切片最大时长（毫秒），避免 ISV 超限。 */
    private int offlineMaxSliceMs = 10000;

    /** 单场最多处理的 distinct speaker 数（如 speaker_0..N）。 */
    private int offlineMaxSpeakers = 8;
}
