package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会议开始响应体。
 * <p>
 * 返回会议 ID 与新状态（始终为 STARTED）。
 * 会前进度已迁至 feishu-scheduled-bot 的事项对比通报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartMeetingResponse {

    /** 会议ID */
    private String meetingId;

    /** 新状态（STARTED） */
    private String newStatus;
}
