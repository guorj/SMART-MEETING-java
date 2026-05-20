package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议开始响应体（F-MID-01）。
 * <p>
 * 返回会议 ID、新状态及可选的上次待办进度，用于飞书或 Web 启动会议后的聚合展示。
 *
 * @see PreviousProgressResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartMeetingResponse {

    /** 会议ID */
    private String meetingId;

    /** 新状态（STARTED → REVIEWING） */
    private String newStatus;

    /** 上次会议待办进度（首次会议为null） */
    private PreviousProgressResponse previousProgress;
}