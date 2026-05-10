package com.smartmeeting.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatterProgressReportResponse {
    /** Markdown 正文，供页面展示 */
    private String analysisMarkdown;
    /** test_data：使用库内测试正文；feishu_doc_pending：已配置飞书字段但拉取未实现 */
    private String source;
    private String configName;
}
