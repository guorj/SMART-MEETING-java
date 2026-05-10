package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议开始响应（F-MID-01）
 *
 * 返回会议ID、新状态、上次进度（可选）
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