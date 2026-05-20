package com.smartmeeting.api.dto.host;

import lombok.Data;

/**
 * 主持当前议题加时请求体（{@code POST /api/v1/host/meetings/{meetingId}/extend-topic}）。
 */
@Data
public class HostExtendTopicRequest {
    /** 加时分钟数，仅允许 1、3、5、10；缺省为 1 */
    private Integer minutes;
}
