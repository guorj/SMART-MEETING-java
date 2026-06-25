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

    private static final String SQL = """
            SELECT
              t.task_name,
              t.business_block,
              assignee.nickname AS assignee_name,
              t.progress,
              CASE t.status
                WHEN 0 THEN '未开始'
                WHEN 1 THEN '进行中'
                WHEN 2 THEN '已完成'
                WHEN 3 THEN '已延期'
              END AS status_label,
              t.start_date,
              t.planned_end_date,
              t.remark
            FROM jq_project_task_tracking t
            LEFT JOIN system_users assignee
              ON assignee.id = t.assignee_user_id
              AND (assignee.deleted = 0 OR assignee.deleted IS NULL)
            WHERE t.deleted = 0
              AND t.remark LIKE '[feishu:recId=%'
            ORDER BY t.planned_end_date
            """;

    @BeforeAll
    static void setUp() {
        OabpDataSourceProperties props = new OabpDataSourceProperties();
        props.setEnabled(true);
        props.setUrl(System.getenv().getOrDefault("DB_OABP_URL",
                "jdbc:mysql://60.205.1.17:3306/oabp?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai"));
        props.setUsername(System.getenv().getOrDefault("DB_USERNAME", "intelligence"));
        props.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "intelligence@2026"));
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
        var sheet = service.queryAsSheet("select * from jq_project_task_tracking limit 5");
        assertNotNull(sheet);
        assertFalse(sheet.getRows().isEmpty());
    }
}
