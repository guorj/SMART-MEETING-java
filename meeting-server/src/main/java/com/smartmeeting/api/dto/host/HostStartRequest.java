package com.smartmeeting.api.dto.host;

import lombok.Data;

import java.util.List;

/**
 * 启动 AI 主持会话请求体（{@code POST /api/v1/host/meetings/{meetingId}/start}，可选 body）。
 * <p>
 * 不传 body 时使用会议创建时写入的 {@code host_agenda} 默认会序。
 */
@Data
public class HostStartRequest {
    /** 会序 JSON schema 版本，便于后续演进 */
    private Integer schemaVersion;
    /** 整场会议预计总时长（分钟） */
    private Integer totalDurationMinutes;
    /** 会序列表，覆盖或补充 DB 中的主持议程 */
    private List<HostAgendaItemDto> items;
}
