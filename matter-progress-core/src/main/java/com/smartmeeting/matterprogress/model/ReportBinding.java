package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/**
 * 上会只读：OUTPUT/BOTH 行的报告绑定。
 * <p>v0.26：优先 {@code generatedReportRunId}（指向 int_weekly_matter_comparison_run.id）；
 * 旧 {@code generatedReportUrl} 保留只读兼容。
 */
public record ReportBinding(
        String configName,
        String generatedReportUrl,
        Instant generatedReportAt,
        Long generatedReportRunId,
        /** OUTPUT 行可选 feishu_doc_url，未进 SOURCE 合并时单独展示 */
        String outputFeishuDocUrl
) {
    /** 是否有任何可展示的通报产物（runId 或 URL）。 */
    public boolean hasAnyReport() {
        return (generatedReportRunId != null) ||
                (generatedReportUrl != null && !generatedReportUrl.isBlank());
    }
}
