package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/** 上会只读：OUTPUT/BOTH 行的报告绑定 */
public record ReportBinding(
        String configName,
        String generatedReportUrl,
        Instant generatedReportAt,
        /** OUTPUT 行可选 feishu_doc_url，未进 SOURCE 合并时单独展示 */
        String outputFeishuDocUrl
) {
}
