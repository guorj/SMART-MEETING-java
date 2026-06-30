package com.smartmeeting.service.oabp;

import com.smartmeeting.config.datasource.OabpDataSourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfEnvironmentVariable(named = "OABP_LIVE_TEST", matches = "true")
class OabpAgendaTaskQueryServiceLiveTest {

    private static HikariDataSource ds;
    private static OabpAgendaTaskQueryService service;

    /** 与 scripts/sql/oabp-task-agenda-jq-todos.sql 及 preset host_agenda oabpTaskSql 一致 */
    private static final String SQL = """
            SELECT
              t.task_name AS `待办事项`,
              t.business_block AS `项目类别`,
              GROUP_CONCAT(DISTINCT u.nickname ORDER BY s.id SEPARATOR '、') AS `执行人`,
              t.progress AS `进度`,
              CASE t.status
                WHEN 0 THEN '未开始'
                WHEN 1 THEN '进行中'
                WHEN 2 THEN '已完成'
                WHEN 3 THEN '已延期'
              END AS `状态`,
              t.start_date AS `开始日期`,
              t.planned_end_date AS `截止日期`,
              NULLIF(TRIM(t.task_detail), '') AS `长期任务`
            FROM jq_todos_task t
            LEFT JOIN jq_todos_subtask s
              ON s.parent_id = t.id
              AND (s.deleted = 0 OR s.deleted IS NULL)
            LEFT JOIN oabp.system_users u
              ON u.id = s.asignee_id
              AND (u.deleted = 0 OR u.deleted IS NULL)
            LEFT JOIN jq_todos_task_followup f
              ON f.task_id = t.id
              AND f.task_type = 0
              AND (f.deleted = 0 OR f.deleted IS NULL)
              AND f.id = (
                SELECT MAX(f2.id)
                FROM jq_todos_task_followup f2
                WHERE f2.task_id = t.id
                  AND f2.task_type = 0
                  AND (f2.deleted = 0 OR f2.deleted IS NULL)
              )
            WHERE (t.deleted = 0 OR t.deleted IS NULL)
              AND t.remark LIKE '[feishu:recId=%'
            GROUP BY
              t.id,
              t.task_name,
              t.business_block,
              t.progress,
              t.status,
              t.start_date,
              t.planned_end_date,
              t.task_detail
            ORDER BY
              CASE t.status
                WHEN 3 THEN 0
                WHEN 2 THEN 1
                WHEN 1 THEN 2
                WHEN 0 THEN 3
                ELSE 4
              END,
              t.planned_end_date,
              t.id
            """;

    @BeforeAll
    static void setUp() {
        OabpDataSourceProperties props = new OabpDataSourceProperties();
        props.setEnabled(true);
        props.setUrl(System.getenv().getOrDefault("DB_OABP_URL",
                "jdbc:mysql://60.205.1.17:3306/oabp_pro?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai"));
        props.setUsername(System.getenv().getOrDefault("DB_USERNAME", "oabp"));
        props.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "oabp@2026"));
        props.setQueryTimeoutSeconds(10);
        ds = props.toHikariDataSource();
        service = new OabpAgendaTaskQueryService(ds, props);
    }

    @AfterAll
    static void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void queryAsSheet_livePresetSql() {
        var sheet = service.queryAsSheet(SQL);
        assertNotNull(sheet);
        assertFalse(sheet.getRows().isEmpty());
    }

    @Test
    void queryAsSheet_selectStar() {
        var sheet = service.queryAsSheet("select * from jq_todos_task limit 5");
        assertNotNull(sheet);
        assertFalse(sheet.getRows().isEmpty());
    }
}
