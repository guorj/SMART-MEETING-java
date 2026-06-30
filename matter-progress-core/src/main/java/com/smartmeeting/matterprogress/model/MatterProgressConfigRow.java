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
        boolean enabled,
        String oabpTaskSql,
        String oabpSchemaHint,
        Long generatedReportRunId
) {
    public static final String DEFAULT_OABP_SCHEMA = "oabp_pro";

    public MatterProgressConfigRow(
            long id,
            String configName,
            Integer presetTypeCode,
            Integer agendaIndex,
            String configRole,
            String feishuDocUrl,
            String generatedReportUrl,
            Instant generatedReportAt,
            boolean enabled) {
        this(id, configName, presetTypeCode, agendaIndex, configRole, feishuDocUrl,
                generatedReportUrl, generatedReportAt, enabled, null, DEFAULT_OABP_SCHEMA, null);
    }

    public MatterProgressConfigRow(
            long id,
            String configName,
            Integer presetTypeCode,
            Integer agendaIndex,
            String configRole,
            String feishuDocUrl,
            String generatedReportUrl,
            Instant generatedReportAt,
            boolean enabled,
            String oabpTaskSql,
            String oabpSchemaHint) {
        this(id, configName, presetTypeCode, agendaIndex, configRole, feishuDocUrl,
                generatedReportUrl, generatedReportAt, enabled, oabpTaskSql, oabpSchemaHint, null);
    }

    public boolean hasOabpTaskSql() {
        return oabpTaskSql != null && !oabpTaskSql.isBlank();
    }

    public String resolvedOabpSchemaHint() {
        return oabpSchemaHint != null && !oabpSchemaHint.isBlank()
                ? oabpSchemaHint.trim()
                : DEFAULT_OABP_SCHEMA;
    }
}
