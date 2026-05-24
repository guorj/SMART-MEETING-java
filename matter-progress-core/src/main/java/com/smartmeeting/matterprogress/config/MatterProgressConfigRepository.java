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
     * 解析 job.source_config_names，返回 enabled 且 feishu_doc_url 非空的行。
     */
    List<MatterProgressConfigRow> loadSources(List<String> configNames);

    /**
     * 写回 bot 产出：只 UPDATE output 行的 generated_report_url / generated_report_at。
     */
    void writeGeneratedReport(String outputConfigName, String reportUrl, Instant generatedAt);

    /**
     * 上会只读：按 preset + agenda_index 查 OUTPUT/BOTH 行。
     */
    Optional<ReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex);
}
