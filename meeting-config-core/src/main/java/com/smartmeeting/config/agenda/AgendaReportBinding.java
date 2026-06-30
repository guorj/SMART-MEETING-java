package com.smartmeeting.config.agenda;

import java.time.LocalDateTime;

/**
 * 会序通报只读绑定。
 * <p>v0.26：优先 {@code generatedReportRunId}（指向 int_weekly_matter_comparison_run.id）；
 * 旧 {@code generatedReportUrl} 保留只读兼容。
 */
public record AgendaReportBinding(
        String generatedReportUrl,
        LocalDateTime generatedReportAt,
        Long generatedReportRunId,
        String outputFeishuDocUrl
) {
    /** 是否有任何可展示的通报产物（runId 或 URL）。 */
    public boolean hasAnyReport() {
        return (generatedReportRunId != null) ||
                (generatedReportUrl != null && !generatedReportUrl.isBlank());
    }
}
