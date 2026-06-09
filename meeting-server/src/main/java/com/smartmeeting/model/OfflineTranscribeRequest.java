package com.smartmeeting.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 会后离线转写请求上下文（PCM 路径 + 参会人声纹 hint）。
 */
@Value
@Builder
public class OfflineTranscribeRequest {

    String meetingId;
    String audioPath;
    List<String> featureIds;
    int participantCount;

    public static OfflineTranscribeRequest of(String meetingId, String audioPath,
                                              List<String> featureIds, int participantCount) {
        return OfflineTranscribeRequest.builder()
                .meetingId(meetingId)
                .audioPath(audioPath)
                .featureIds(featureIds != null ? featureIds : List.of())
                .participantCount(Math.max(0, participantCount))
                .build();
    }
}
