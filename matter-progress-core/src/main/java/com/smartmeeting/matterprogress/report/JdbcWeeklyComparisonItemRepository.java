package com.smartmeeting.matterprogress.report;

import com.smartmeeting.matterprogress.model.WeeklyComparisonItem;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** 批量写 int_weekly_matter_comparison_item（每行一个事项） */
public class JdbcWeeklyComparisonItemRepository {

    private final JdbcTemplate jdbc;

    public JdbcWeeklyComparisonItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 批量插入事项行；assignee 为 null 时存 NULL（前端显示「未提及」）。
     */
    public void batchInsert(long runId, List<WeeklyComparisonItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO int_weekly_matter_comparison_item
                    (run_id, category, matter_name, assignee, time_node, status_label, sort_order, source_config_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.batchUpdate(sql, items, items.size(), (ps, item) -> {
            ps.setLong(1, runId);
            ps.setString(2, item.category());
            ps.setString(3, item.matterName());
            ps.setString(4, item.assignee());
            ps.setString(5, item.timeNode());
            ps.setString(6, item.statusLabel());
            ps.setInt(7, item.sortOrder());
            ps.setString(8, item.sourceConfigName());
        });
    }
}
