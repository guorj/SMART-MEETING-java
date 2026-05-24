package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 议程资料纯文本响应体（{@code GET /api/v1/meetings/{id}/agenda-doc-content}）。
 * <p>
 * 按会序返回飞书 Docx/Wiki 等资料的合并正文及分项列表，供主持页只读展示。
 */
@Data
@Builder
public class AgendaDocContentResponse {
    private int agendaIndex;
    private String agendaTitle;
    /** 首条资料 documentId（兼容旧前端） */
    private String documentId;
    private String feishuDocUrl;
    private String docKind;
    /** 合并正文（各 part 用标题分隔） */
    private String plainText;
    /** 同一会序多条资料分项 */
    private List<AgendaDocPartDto> parts;
    /** 会前事项对比通报（OUTPUT/BOTH 的 generated_report_url 正文，可为 null） */
    private AgendaWeeklyReportDto weeklyReport;
}
