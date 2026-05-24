package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/** 配置表一行（读写 bot / 上会展示） */
public record MatterProgressConfigRow(
        long id,
        String configName,
        Integer presetTypeCode,
        Integer agendaIndex,
        String configRole,
        String feishuDocUrl,
        String generatedReportUrl,
        Instant generatedReportAt,
        boolean enabled
) {
}
