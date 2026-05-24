package com.smartmeeting.matterprogress.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.jdbc.JdbcRowHelpers;
import com.smartmeeting.matterprogress.model.WeeklyComparisonJob;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 读写 int_weekly_matter_comparison_job */
public class JdbcWeeklyComparisonJobRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcWeeklyComparisonJobRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<WeeklyComparisonJob> findAllEnabled() {
        return jdbc.query(
                """
                SELECT id, job_name, enabled, cron_expression, schedule_timezone,
                       source_config_names, minute_query_type, minute_query_params,
                       output_config_name, output_doc_title_tpl, feishu_folder_token
                FROM int_weekly_matter_comparison_job
                WHERE enabled = 1
                ORDER BY id
                """,
                (rs, rowNum) -> mapRow(rs));
    }

    public List<WeeklyComparisonJob> findAll() {
        return jdbc.query(
                """
                SELECT id, job_name, enabled, cron_expression, schedule_timezone,
                       source_config_names, minute_query_type, minute_query_params,
                       output_config_name, output_doc_title_tpl, feishu_folder_token
                FROM int_weekly_matter_comparison_job
                ORDER BY id
                """,
                (rs, rowNum) -> mapRow(rs));
    }

    public Optional<WeeklyComparisonJob> findById(long id) {
        List<WeeklyComparisonJob> rows = jdbc.query(
                """
                SELECT id, job_name, enabled, cron_expression, schedule_timezone,
                       source_config_names, minute_query_type, minute_query_params,
                       output_config_name, output_doc_title_tpl, feishu_folder_token
                FROM int_weekly_matter_comparison_job
                WHERE id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateRunResult(long jobId, String status, String error, Instant at) {
        jdbc.update(
                """
                UPDATE int_weekly_matter_comparison_job
                SET last_run_at = ?, last_run_status = ?, last_run_error = ?, updated_at = NOW()
                WHERE id = ?
                """,
                java.sql.Timestamp.from(at != null ? at : Instant.now()),
                status,
                error,
                jobId);
    }

    private WeeklyComparisonJob mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> names;
        try {
            names = objectMapper.readValue(rs.getString("source_config_names"), STRING_LIST);
        } catch (Exception e) {
            names = List.of();
        }
        return new WeeklyComparisonJob(
                rs.getLong("id"),
                rs.getString("job_name"),
                JdbcRowHelpers.readEnabledFlag(rs, "enabled"),
                rs.getString("cron_expression"),
                rs.getString("schedule_timezone"),
                names,
                rs.getString("minute_query_type"),
                rs.getString("minute_query_params"),
                rs.getString("output_config_name"),
                rs.getString("output_doc_title_tpl"),
                rs.getString("feishu_folder_token"));
    }
}
