package com.smartmeeting.api.dto.internal;

import lombok.Builder;
import lombok.Value;

/**
 * 单会议 ISV 声纹重标注结果（不重跑 IST、不 regen 纪要）。
 */
@Value
@Builder
public class VoiceprintRelabelResult {
    String meetingId;
    int segmentCount;
    int updatedCount;
    String audioPathUsed;
    String message;
}
