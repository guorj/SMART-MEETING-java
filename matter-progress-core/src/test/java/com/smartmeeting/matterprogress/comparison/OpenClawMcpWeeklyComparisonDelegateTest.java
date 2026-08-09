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
    void validateSourceRows_okWhenSourceRowsEmpty() {
        WeeklyComparisonJob job = sampleJob(List.of());
        assertThat(OpenClawMcpWeeklyComparisonDelegate.validateSourceRows(List.of(), job)).isNull();
    }

    @Test
    void validateSourceRows_failsWhenOutputConfigMissing() {
        WeeklyComparisonJob job = new WeeklyComparisonJob(
                1L, "test-job", true, "0 10 * * MON", "Asia/Shanghai",
                List.of(), "PRESET_LAST_7_DAYS", "{\"presetTypeCode\":1,\"days\":7}",
                "", "通报-{date}", null);
        assertThat(OpenClawMcpWeeklyComparisonDelegate.validateSourceRows(List.of(), job))
                .contains("output_config_name");
    }

    @Test
    void validateSourceRows_okWhenSqlEmpty() {
        WeeklyComparisonJob job = sampleJob(List.of("cfg-a"));
        MatterProgressConfigRow row = sourceRow("cfg-a", null);
        assertThat(OpenClawMcpWeeklyComparisonDelegate.validateSourceRows(List.of(row), job)).isNull();
    }

    @Test
    void buildPrompt_alwaysEmitsOabpTodosSqlRegardlessOfPresetSql() throws Exception {
        WeeklyComparisonJob job = sampleJob(List.of("cfg-a", "cfg-b"));
        MatterProgressConfigRow withSql = sourceRow("cfg-a", "SELECT 1 AS n");
        MatterProgressConfigRow withoutSql = sourceRow("cfg-b", null);
        var m = OpenClawMcpWeeklyComparisonDelegate.class.getDeclaredMethod(
                "buildMcpSkillPrompt",
                WeeklyComparisonJob.class,
                List.class,
                Optional.class,
                boolean.class,
                String.class);
        m.setAccessible(true);
        String prompt = (String) m.invoke(delegate, job, List.of(withSql, withoutSql), Optional.empty(), false, "task-1");
        // [oabp_todos_sql] 固定三表 SELECT，与 preset oabpTaskSql 是否填写无关
        assertThat(prompt).contains("[oabp_todos_sql]");
        assertThat(prompt).contains("jq_todos_task");
        assertThat(prompt).contains("jq_todos_subtask");
        assertThat(prompt).contains("jq_todos_task_followup");
        // 旧 [source_oabp_sql] 段已移除，preset 的 oabpTaskSql 不再下发
        assertThat(prompt).doesNotContain("[source_oabp_sql]");
        assertThat(prompt).doesNotContain("SELECT 1 AS n");
        // [run_insert_payload] 段存在
        assertThat(prompt).contains("[run_insert_payload]");
        assertThat(prompt).contains("int_weekly_matter_comparison_run");
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
    void buildPromptContainsOabpTodosSqlAndRunInsertPayload() throws Exception {
        WeeklyComparisonJob job = sampleJob(List.of("preset1-comp-agenda-01"));
        MatterProgressConfigRow outputRow = new MatterProgressConfigRow(
                2L, "preset1-weekly-report-out", 1, 0, "OUTPUT", null, null, null, true);
        var m = OpenClawMcpWeeklyComparisonDelegate.class.getDeclaredMethod(
                "buildMcpSkillPrompt",
                WeeklyComparisonJob.class,
                List.class,
                Optional.class,
                boolean.class,
                String.class);
        m.setAccessible(true);
        String prompt = (String) m.invoke(delegate, job, List.of(), Optional.of(outputRow), false, "task-1");
        assertThat(prompt).contains("[oabp_todos_sql]");
        assertThat(prompt).contains("section=main_task");
        assertThat(prompt).contains("section=subtask");
        assertThat(prompt).contains("section=followup_latest");
        assertThat(prompt).contains("[run_insert_payload]");
        assertThat(prompt).contains("columns=job_id,output_config_name,preset_type_code,agenda_index,title");
        assertThat(prompt).doesNotContain("[source_oabp_sql]");
        assertThat(prompt).doesNotContain("[source_feishu_urls]");
        assertThat(prompt).doesNotContain("[output_reference_url]");
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
