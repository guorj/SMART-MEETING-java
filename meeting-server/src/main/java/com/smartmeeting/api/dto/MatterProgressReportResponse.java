package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 事项进度通报响应体（{@code POST /api/v1/meetings/{id}/matter-progress-report}）。
 * <p>
 * {@code source} 示例：{@code test_data}（classpath 样例）、{@code feishu_doc_pending} 等。
 */
@Data
@Builder
public class MatterProgressReportResponse {
    /** Markdown 正文，供页面展示 */
    private String analysisMarkdown;
    /** test_data：使用库内测试正文；feishu_doc_pending：已配置飞书字段但拉取未实现 */
    private String source;
    /** 使用的文档配置名称 */
    private String configName;
}
