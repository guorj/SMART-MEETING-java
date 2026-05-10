package com.smartmeeting.asr;

import lombok.Getter;
import lombok.Setter;

/**
 * ASR 识别结果
 */
@Getter
@Setter
public class AsrResult {
    private String meetingId;
    private String text;
    private Double confidence;
    private Boolean finalResult;  // 是否最终结果（type=0）
    private Boolean isLast;       // 是否最后一条结果（ls=true）
    private Integer startTimeMs;
    private Integer endTimeMs;
    private String speakerId;
}
