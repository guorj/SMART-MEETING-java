package com.smartmeeting.matterprogress.config;

import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.ReportBinding;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 配置表访问：bot 写回与上会只读查询。
 */
public interface MatterProgressConfigRepository {

    /** 按 config_name 查一行；不存在则 Optional.empty() */
    Optional<MatterProgressConfigRow> findByConfigName(String configName);

    /**
     * 解析 job.source_config_names，返回 enabled 且 oabpTaskSql 非空的 SOURCE/BOTH 行。
     */
    List<MatterProgressConfigRow> loadSources(List<String> configNames);

    /**
     * 写回 bot 产出（v0.9 飞书 URL，保留只读兼容）。
     */
    void writeGeneratedReport(String outputConfigName, String reportUrl, Instant generatedAt);

    /**
     * v0.26：写回最新 run id + 生成时间到 host_agenda JSON（不再写 URL）。
     */
    void writeGeneratedReportRun(String outputConfigName, long runId, Instant generatedAt);

    /**
     * 上会只读：按 preset + agenda_index 查 OUTPUT/BOTH 行。
     * <p>过滤条件：runId != null 或 url 非空（双兼容）。
     */
    Optional<ReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex);
}
