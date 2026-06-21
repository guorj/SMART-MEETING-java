package com.smartmeeting.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 会议改期请求（{@code PATCH /api/v1/meetings/{id}/schedule}）。 */
@Data
public class MeetingScheduleRequest {
    private LocalDateTime scheduledTime;
    /** 日历时长（分钟），仅用于飞书 end_time，默认 60 */
    private Integer durationMinutes;
    /** 是否 PATCH 同步飞书日历（room_id 有 event_id 时） */
    private Boolean syncCalendar;
    /** 是否向群 chat_id 发送改期文本通知 */
    private Boolean notifyChat;
}
