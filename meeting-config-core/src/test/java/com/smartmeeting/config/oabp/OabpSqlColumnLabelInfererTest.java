package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OabpSqlColumnLabelInfererTest {

    @Test
    void infer_readsAsAlias() {
        String sql = "SELECT task_name AS 待办事项, status_code AS 状态 FROM jq_project_task_tracking WHERE deleted = 0";
        Map<String, String> labels = OabpSqlColumnLabelInferer.infer(sql, List.of("task_name", "status_code"));
        assertEquals("待办事项", labels.get("task_name"));
        assertEquals("状态", labels.get("status_code"));
    }
}
