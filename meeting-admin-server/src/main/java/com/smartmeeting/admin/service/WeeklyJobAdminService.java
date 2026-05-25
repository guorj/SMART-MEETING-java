package com.smartmeeting.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.api.dto.WeeklyJobDto;
import com.smartmeeting.admin.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WeeklyJobAdminService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public List<WeeklyJobDto> listAll() {
        return jdbc.query(
                """
                SELECT id, job_name, enabled, cron_expression, schedule_timezone,
                       source_config_names, minute_query_type, minute_query_params,
                       output_config_name, output_doc_title_tpl, feishu_folder_token,
                       last_run_at, last_run_status, last_run_error
                FROM int_weekly_matter_comparison_job
                ORDER BY id
                """,
                (rs, i) -> mapRow(rs));
    }

    public WeeklyJobDto get(long id) {
        return listAll().stream().filter(j -> j.getId() == id).findFirst()
                .orElseThrow(() -> new BusinessException("job not found"));
    }

    public long create(WeeklyJobDto dto) {
        validateJob(dto);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO int_weekly_matter_comparison_job (
                      job_name, enabled, cron_expression, schedule_timezone,
                      source_config_names, minute_query_type, minute_query_params,
                      output_config_name, output_doc_title_tpl, feishu_folder_token
                    ) VALUES (?,?,?,?,?,?,?,?,?,?)
                    """,
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, dto.getJobName());
            ps.setInt(i++, dto.isEnabled() ? 1 : 0);
            ps.setString(i++, dto.getCronExpression());
            ps.setString(i++, dto.getScheduleTimezone() != null ? dto.getScheduleTimezone() : "Asia/Shanghai");
            ps.setString(i++, toJson(dto.getSourceConfigNames()));
            ps.setString(i++, dto.getMinuteQueryType());
            ps.setString(i++, dto.getMinuteQueryParamsJson());
            ps.setString(i++, dto.getOutputConfigName());
            ps.setString(i++, dto.getOutputDocTitleTpl() != null ? dto.getOutputDocTitleTpl() : "事项对比通报-{date}");
            ps.setString(i++, dto.getFeishuFolderToken());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    public void update(long id, WeeklyJobDto dto) {
        validateJob(dto);
        int n = jdbc.update(
                """
                UPDATE int_weekly_matter_comparison_job SET
                  job_name=?, enabled=?, cron_expression=?, schedule_timezone=?,
                  source_config_names=?, minute_query_type=?, minute_query_params=?,
                  output_config_name=?, output_doc_title_tpl=?, feishu_folder_token=?,
                  updated_at=NOW()
                WHERE id=?
                """,
                dto.getJobName(),
                dto.isEnabled() ? 1 : 0,
                dto.getCronExpression(),
                dto.getScheduleTimezone() != null ? dto.getScheduleTimezone() : "Asia/Shanghai",
                toJson(dto.getSourceConfigNames()),
                dto.getMinuteQueryType(),
                dto.getMinuteQueryParamsJson(),
                dto.getOutputConfigName(),
                dto.getOutputDocTitleTpl(),
                dto.getFeishuFolderToken(),
                id);
        if (n == 0) {
            throw new BusinessException("job not found");
        }
    }

    public void delete(long id) {
        if (jdbc.update("DELETE FROM int_weekly_matter_comparison_job WHERE id=?", id) == 0) {
            throw new BusinessException("job not found");
        }
    }

    private void validateJob(WeeklyJobDto dto) {
        if (dto.getJobName() == null || dto.getJobName().isBlank()) {
            throw new BusinessException("job_name 必填");
        }
        if (dto.getCronExpression() == null || dto.getCronExpression().isBlank()) {
            throw new BusinessException("cron_expression 必填");
        }
        if (dto.getSourceConfigNames() == null || dto.getSourceConfigNames().isEmpty()) {
            throw new BusinessException("source_config_names 至少一项");
        }
        if (dto.getOutputConfigName() == null || dto.getOutputConfigName().isBlank()) {
            throw new BusinessException("output_config_name 必填");
        }
        try {
            objectMapper.readTree(dto.getMinuteQueryParamsJson());
        } catch (Exception e) {
            throw new BusinessException("minute_query_params 须为合法 JSON");
        }
    }

    private String toJson(List<String> names) {
        try {
            return objectMapper.writeValueAsString(names != null ? names : List.of());
        } catch (Exception e) {
            throw new BusinessException("invalid source_config_names");
        }
    }

    private WeeklyJobDto mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> names;
        try {
            names = objectMapper.readValue(rs.getString("source_config_names"), STRING_LIST);
        } catch (Exception e) {
            names = List.of();
        }
        Timestamp lastRun = rs.getTimestamp("last_run_at");
        return WeeklyJobDto.builder()
                .id(rs.getLong("id"))
                .jobName(rs.getString("job_name"))
                .enabled(rs.getInt("enabled") == 1)
                .cronExpression(rs.getString("cron_expression"))
                .scheduleTimezone(rs.getString("schedule_timezone"))
                .sourceConfigNames(names)
                .minuteQueryType(rs.getString("minute_query_type"))
                .minuteQueryParamsJson(rs.getString("minute_query_params"))
                .outputConfigName(rs.getString("output_config_name"))
                .outputDocTitleTpl(rs.getString("output_doc_title_tpl"))
                .feishuFolderToken(rs.getString("feishu_folder_token"))
                .lastRunAt(lastRun != null ? lastRun.toInstant() : null)
                .lastRunStatus(rs.getString("last_run_status"))
                .lastRunError(rs.getString("last_run_error"))
                .build();
    }
}
