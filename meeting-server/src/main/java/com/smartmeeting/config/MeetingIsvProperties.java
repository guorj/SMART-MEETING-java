package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 讯飞 ISV 声纹识别配置，绑定 {@code meeting.isv.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.isv")
public class MeetingIsvProperties {

    private boolean enabled = false;
    private String groupId = "smart_meeting_vp";
    /** 1:N 匹配最低置信度（高于此值才接受）。 */
    private double matchScoreThreshold = 0.6;

    /** ISV searchFea 返回 topK 上限（讯飞 API 上限 10）。 */
    private int searchTopKMax = 10;

    /** 离线标注请求 ISV 时的 topK 下限。 */
    private int searchTopKMin = 3;

    /** ISV 比对 PCM 切片最小字节数（16k s16le）。 */
    private int minSliceBytes = 1600;

    /** 参与 ISV 切片的句段最小时长（毫秒）。 */
    private int minSegmentMsForSlice = 500;

    private double searchMinAudioSec = 3.0;
    private int apiTimeout = 8;
    private double registerTimeout = 5.0;
    private boolean debugLog = false;
    private int ttlYears = 10;
}
