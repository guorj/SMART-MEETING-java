package com.smartmeeting.matterprogress.config;

import com.smartmeeting.matterprogress.jdbc.JdbcRowHelpers;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.ReportBinding;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC 实现 {@link MatterProgressConfigRepository} */
public class JdbcMatterProgressConfigRepository implements MatterProgressConfigRepository {

    private static final RowMapper<MatterProgressConfigRow> ROW_MAPPER = JdbcMatterProgressConfigRepository::mapRow;

    private final JdbcTemplate jdbc;

    public JdbcMatterProgressConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MatterProgressConfigRow> findByConfigName(String configName) {
        if (configName == null || configName.isBlank()) {
            return Optional.empty();
        }
        List<MatterProgressConfigRow> rows = jdbc.query(
                """
                SELECT id, config_name, preset_type_code, agenda_index, config_role,
                       feishu_doc_url, generated_report_url, generated_report_at, enabled
                FROM int_matter_progress_doc_config
                WHERE config_name = ?
                LIMIT 1
                """,
                ROW_MAPPER,
                configName.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<MatterProgressConfigRow> loadSources(List<String> configNames) {
        if (configNames == null || configNames.isEmpty()) {
            return List.of();
        }
        List<MatterProgressConfigRow> out = new ArrayList<>();
        for (String name : configNames) {
            findByConfigName(name).ifPresent(row -> {
                if (row.enabled() && row.feishuDocUrl() != null && !row.feishuDocUrl().isBlank()) {
                    out.add(row);
                }
            });
        }
        return out;
    }

    @Override
    public void writeGeneratedReport(String outputConfigName, String reportUrl, Instant generatedAt) {
        if (outputConfigName == null || outputConfigName.isBlank()) {
            throw new IllegalArgumentException("outputConfigName 为空");
        }
        Timestamp ts = generatedAt != null ? Timestamp.from(generatedAt) : Timestamp.from(Instant.now());
        int n = jdbc.update(
                """
                UPDATE int_matter_progress_doc_config
                SET generated_report_url = ?, generated_report_at = ?, updated_at = NOW()
                WHERE config_name = ?
                """,
                reportUrl,
                ts,
                outputConfigName.trim());
        if (n == 0) {
            throw new IllegalStateException("写回失败，未找到 config_name=" + outputConfigName);
        }
    }

    @Override
    public Optional<ReportBinding> findReportBindingForAgenda(int presetTypeCode, int agendaIndex) {
        List<ReportBinding> rows = jdbc.query(
                """
                SELECT config_name, generated_report_url, generated_report_at, feishu_doc_url, config_role
                FROM int_matter_progress_doc_config
                WHERE enabled = 1
                  AND preset_type_code = ?
                  AND agenda_index = ?
                  AND config_role IN ('OUTPUT', 'BOTH')
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> toBinding(rs),
                presetTypeCode,
                agendaIndex);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static ReportBinding toBinding(ResultSet rs) throws SQLException {
        String configName = rs.getString("config_name");
        String reportUrl = rs.getString("generated_report_url");
        Timestamp at = rs.getTimestamp("generated_report_at");
        String feishuUrl = rs.getString("feishu_doc_url");
        String role = rs.getString("config_role");
        String outputFeishu = null;
        if (ConfigRoles.OUTPUT.equalsIgnoreCase(role != null ? role.trim() : "")
                && feishuUrl != null && !feishuUrl.isBlank()) {
            outputFeishu = feishuUrl.trim();
        } else if (ConfigRoles.BOTH.equalsIgnoreCase(role != null ? role.trim() : "")) {
            outputFeishu = null;
        }
        return new ReportBinding(
                configName,
                reportUrl != null ? reportUrl.trim() : null,
                at != null ? at.toInstant() : null,
                outputFeishu);
    }

    private static MatterProgressConfigRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp genAt = rs.getTimestamp("generated_report_at");
        return new MatterProgressConfigRow(
                rs.getLong("id"),
                rs.getString("config_name"),
                JdbcRowHelpers.readInteger(rs, "preset_type_code"),
                JdbcRowHelpers.readInteger(rs, "agenda_index"),
                rs.getString("config_role"),
                rs.getString("feishu_doc_url"),
                rs.getString("generated_report_url"),
                genAt != null ? genAt.toInstant() : null,
                JdbcRowHelpers.readEnabledFlag(rs, "enabled"));
    }
}
