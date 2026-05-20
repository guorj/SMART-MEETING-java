package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会议纪要查询响应体（{@code GET /api/v1/meetings/{id}/minute}）。
 * <p>
 * {@code source} 取值：{@code database} | {@code feishu_only} | {@code none}。
 */
@Data
@Builder
public class MinuteResponse {
    private String meetingId;
    /** database | feishu_only | none */
    private String source;
    /** 库内 Markdown 正文 */
    private String contentMarkdown;
    private String docUrl;
    private String docToken;
    private String generationStatus;
    private LocalDateTime generatedAt;
    private Boolean hasMinute;
}
