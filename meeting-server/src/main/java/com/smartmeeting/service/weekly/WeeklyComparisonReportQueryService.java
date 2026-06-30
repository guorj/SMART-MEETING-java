package com.smartmeeting.service.weekly;

import com.smartmeeting.api.dto.WeeklyComparisonItemDto;
import com.smartmeeting.api.dto.WeeklyComparisonRunDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

/**
 * 主持页读 {@code int_weekly_matter_comparison_run} + {@code _item}，
 * 按 run_id 返回批次头 + 事项列表（按 category, sort_order 排序）。
 */
@Slf4j
@Service
public class WeeklyComparisonReportQueryService {

    private final JdbcTemplate jdbc;

    public WeeklyComparisonReportQueryService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public Optional<WeeklyComparisonRunDto> findRunById(long runId) {
        List<WeeklyComparisonRunDto> rows = jdbc.query("""
                SELECT id, title, item_count, generation_status, generated_at, run_error
                FROM int_weekly_matter_comparison_run
                WHERE id = ?
                """,
                (rs, rowNum) -> WeeklyComparisonRunDto.builder()
                        .id(rs.getLong("id"))
                        .title(rs.getString("title"))
                        .itemCount(rs.getInt("item_count"))
                        .generationStatus(rs.getString("generation_status"))
                        .generatedAt(rs.getTimestamp("generated_at") != null
                                ? rs.getTimestamp("generated_at").toLocalDateTime()
                                : null)
                        .runError(rs.getString("run_error"))
                        .build(),
                runId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 按 category 固定顺序 + sort_order 排序返回事项。
     */
    public List<WeeklyComparisonItemDto> findItemsByRunId(long runId) {
        return jdbc.query("""
                SELECT id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name
                FROM int_weekly_matter_comparison_item
                WHERE run_id = ?
                ORDER BY FIELD(category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'), sort_order
                """,
                (rs, rowNum) -> WeeklyComparisonItemDto.builder()
                        .id(rs.getLong("id"))
                        .category(rs.getString("category"))
                        .matterName(rs.getString("matter_name"))
                        .assignee(rs.getString("assignee"))
                        .timeNode(rs.getString("time_node"))
                        .statusLabel(rs.getString("status_label"))
                        .sortOrder(rs.getInt("sort_order"))
                        .sourceConfigName(rs.getString("source_config_name"))
                        .build(),
                runId);
    }
}
