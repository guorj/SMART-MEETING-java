package com.smartmeeting.config.agenda;

import java.time.LocalDateTime;

/** 会序通报只读绑定（OUTPUT/BOTH 行的 generated_report_url）。 */
public record AgendaReportBinding(
        String generatedReportUrl,
        LocalDateTime generatedReportAt,
        String outputFeishuDocUrl) {
}
