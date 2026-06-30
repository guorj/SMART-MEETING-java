package com.smartmeeting.matterprogress.report;

import com.smartmeeting.matterprogress.model.WeeklyComparisonRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/** 读写 int_weekly_matter_comparison_run */
public class JdbcWeeklyComparisonRunRepository {

    private final JdbcTemplate jdbc;

    public JdbcWeeklyComparisonRunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入 run 批次头，返回自增 id。
     */
    public long insertRun(WeeklyComparisonRun run) {
        LocalDateTime at = run.generatedAt() != null
                ? LocalDateTime.ofInstant(run.generatedAt(), ZoneId.systemDefault())
                : LocalDateTime.now();
        String sql = """
                INSERT INTO int_weekly_matter_comparison_run
                    (job_id, output_config_name, preset_type_code, agenda_index, title,
                     item_count, generation_status, generated_at, run_error)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, run.jobId());
            ps.setString(2, run.outputConfigName());
            ps.setObject(3, run.presetTypeCode());
            ps.setObject(4, run.agendaIndex());
            ps.setString(5, run.title());
            ps.setInt(6, run.itemCount());
            ps.setString(7, run.generationStatus() != null ? run.generationStatus() : WeeklyComparisonRun.READY);
            ps.setTimestamp(8, java.sql.Timestamp.valueOf(at));
            ps.setString(9, run.runError());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("insertRun 未返回自增 id");
        }
        return key.longValue();
    }

    /** 回填 item_count 与最终状态（PARTIAL/READY）。 */
    public void updateStatus(long runId, int itemCount, String status, String runError) {
        jdbc.update("""
                UPDATE int_weekly_matter_comparison_run
                SET item_count = ?, generation_status = ?, run_error = ?
                WHERE id = ?
                """,
                itemCount, status, runError, runId);
    }

    /** 标记 run 失败（解析失败时不写 items）。 */
    public void markFailed(long runId, String error) {
        updateStatus(runId, 0, WeeklyComparisonRun.FAILED, error);
    }

    public Optional<WeeklyComparisonRun> findById(long runId) {
        return jdbc.query("""
                SELECT id, job_id, output_config_name, preset_type_code, agenda_index, title,
                       item_count, generation_status, generated_at, run_error
                FROM int_weekly_matter_comparison_run
                WHERE id = ?
                """,
                (rs, rowNum) -> new WeeklyComparisonRun(
                        rs.getLong("id"),
                        (Long) rs.getObject("job_id"),
                        rs.getString("output_config_name"),
                        (Integer) rs.getObject("preset_type_code"),
                        (Integer) rs.getObject("agenda_index"),
                        rs.getString("title"),
                        rs.getInt("item_count"),
                        rs.getString("generation_status"),
                        rs.getTimestamp("generated_at") != null
                                ? rs.getTimestamp("generated_at").toInstant()
                                : null,
                        rs.getString("run_error")),
                runId).stream().findFirst();
    }
}
