package com.smartmeeting.config.oabp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 内置 oabp SQL 数据源预设注册表。 */
public final class OabpSqlPresetRegistry {

    private static final String PROJECT_TASK_SQL = """
            SELECT task_name,
                   status_code,
                   progress,
                   plan_date
            FROM jq_project_task_tracking
            WHERE deleted = 0
              AND status_code != 99
            LIMIT 500""";

    private static final String TODO_FOLLOWUP_SQL = """
            SELECT task_name,
                   status_code,
                   progress,
                   plan_date
            FROM jq_todos_task
            WHERE deleted = 0
            LIMIT 500""";

    private static final Map<String, OabpSqlPreset> PRESETS = new LinkedHashMap<>();

    static {
        register(OabpSqlPreset.builder()
                .id("project_task_default")
                .name("项目任务列表")
                .description("从 jq_project_task_tracking 读取项目任务，排除已归档（status_code=99）")
                .sql(PROJECT_TASK_SQL)
                .build());
        register(OabpSqlPreset.builder()
                .id("todo_followup_default")
                .name("待办跟进列表")
                .description("从 jq_todos_task 读取待办跟进任务")
                .sql(TODO_FOLLOWUP_SQL)
                .build());
    }

    private OabpSqlPresetRegistry() {
    }

    private static void register(OabpSqlPreset preset) {
        PRESETS.put(preset.getId(), preset);
    }

    public static List<OabpSqlPreset> list() {
        return List.copyOf(PRESETS.values());
    }

    public static Optional<OabpSqlPreset> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRESETS.get(id.trim()));
    }

    public static boolean matchesPresetSql(String presetId, String sql) {
        if (presetId == null || presetId.isBlank() || sql == null || sql.isBlank()) {
            return false;
        }
        return find(presetId)
                .map(p -> normalize(p.getSql()).equals(normalize(sql)))
                .orElse(false);
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
