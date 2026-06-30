package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.WeeklyComparisonJob;
import com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OpenClawMcpWeeklyComparisonDelegateTest {

    private final OpenClawMcpWeeklyComparisonDelegate delegate = new OpenClawMcpWeeklyComparisonDelegate(
            new OpenClawGatewayWsClient(),
            new ObjectMapper(),
            "",
            "",
            "",
            "",
            60,
            true,
            "cli_test",
            "oabp_pro");

    @Test
    void validateSourceRows_requiresOabpSql() {
        WeeklyComparisonJob job = sampleJob(List.of("cfg-a"));
        MatterProgressConfigRow row = sourceRow("cfg-a", null);
        assertThat(OpenClawMcpWeeklyComparisonDelegate.validateSourceRows(List.of(row), job))
                .contains("oabpTaskSql");
    }

    @Test
    void validateSourceRows_okWhenSqlPresent() {
        WeeklyComparisonJob job = sampleJob(List.of("cfg-a"));
        MatterProgressConfigRow row = sourceRow("cfg-a", "SELECT 1");
        assertThat(OpenClawMcpWeeklyComparisonDelegate.validateSourceRows(List.of(row), job)).isNull();
    }

    @Test
    void execute_failedWhenSourceMissingSql() {
        WeeklyComparisonJob job = sampleJob(List.of("cfg-a"));
        MatterProgressConfigRow row = sourceRow("cfg-a", "SELECT id FROM t");
        var parser = new com.smartmeeting.matterprogress.report.WeeklyComparisonItemsJsonParser(new ObjectMapper());
        var result = delegate.execute(job, List.of(row), Optional.empty(), false, parser);
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Gateway");
    }

    @Test
    void buildPromptContainsSourceOabpSql() throws Exception {
        WeeklyComparisonJob job = sampleJob(List.of("preset1-comp-agenda-01"));
        MatterProgressConfigRow row = new MatterProgressConfigRow(
                1L, "preset1-comp-agenda-01", 1, 0, "SOURCE", null, null, null, true,
                "SELECT task_name FROM jq_project_task_tracking WHERE deleted = 0",
                "oabp_pro");
        var m = OpenClawMcpWeeklyComparisonDelegate.class.getDeclaredMethod(
                "buildMcpSkillPrompt",
                WeeklyComparisonJob.class,
                List.class,
                Optional.class,
                boolean.class,
                String.class);
        m.setAccessible(true);
        String prompt = (String) m.invoke(delegate, job, List.of(row), Optional.empty(), false, "task-1");
        assertThat(prompt).contains("[source_oabp_sql]");
        assertThat(prompt).contains("jq_project_task_tracking");
        assertThat(prompt).doesNotContain("[source_feishu_urls]");
    }

    private static WeeklyComparisonJob sampleJob(List<String> sourceNames) {
        return new WeeklyComparisonJob(
                1L,
                "test-job",
                true,
                "0 10 * * MON",
                "Asia/Shanghai",
                sourceNames,
                "PRESET_LAST_7_DAYS",
                "{\"presetTypeCode\":1,\"days\":7}",
                "preset1-weekly-report-out",
                "通报-{date}",
                null);
    }

    private static MatterProgressConfigRow sourceRow(String name, String sql) {
        return new MatterProgressConfigRow(
                1L, name, 1, 0, "SOURCE", null, null, null, true, sql, "oabp_pro");
    }
}
