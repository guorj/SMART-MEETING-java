package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OabpPresetRegistryTest {

    @Test
    void sqlPresets_includeProjectTask() {
        assertTrue(OabpSqlPresetRegistry.find("project_task_default").isPresent());
        assertEquals(2, OabpSqlPresetRegistry.list().size());
    }

    @Test
    void displayPresets_copyIsIndependent() {
        OabpDisplayTemplate t1 = OabpDisplayPresetRegistry.copyTemplate("project_task_grouped");
        assertNotNull(t1);
        assertEquals("grouped_table", t1.getDisplayMode());
        t1.setSheetName("修改后");
        OabpDisplayTemplate t2 = OabpDisplayPresetRegistry.copyTemplate("project_task_grouped");
        assertEquals("项目任务", t2.getSheetName());
    }

    @Test
    void sqlPreset_matchesNormalizedSql() {
        String sql = OabpSqlPresetRegistry.find("project_task_default").orElseThrow().getSql();
        assertTrue(OabpSqlPresetRegistry.matchesPresetSql("project_task_default", sql));
        assertFalse(OabpSqlPresetRegistry.matchesPresetSql("project_task_default", "SELECT 1"));
    }
}
