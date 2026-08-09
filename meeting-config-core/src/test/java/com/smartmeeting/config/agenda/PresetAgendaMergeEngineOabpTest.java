package com.smartmeeting.config.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.oabp.OabpDisplayColumn;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 PresetAgendaMergeEngine 在会议快照合并/刷新时保留并补齐 oabp 字段，
 * 使主持页在无资料绑定（bindings=[]）时也能展示项目任务表格。
 */
class PresetAgendaMergeEngineOabpTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static OabpDisplayTemplate sampleTemplate() {
        OabpDisplayColumn col = new OabpDisplayColumn();
        col.setSource("task_name");
        col.setLabel("待办事项");
        col.setVisible(true);
        OabpDisplayTemplate t = new OabpDisplayTemplate();
        t.setDisplayMode("table");
        t.setSheetName("项目任务");
        t.setColumns(List.of(col));
        return t;
    }

    @Test
    void syncHostAgendaJson_preservesOabpFieldsWhenBindingsEmpty() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("管小慧汇报");
        item.setMinutes(4);
        item.setOabpTaskSql("SELECT task_name AS 待办事项 FROM jq_project_task_tracking WHERE deleted = 0");
        item.setOabpTaskShow(true);
        item.setOabpDisplayTemplate(sampleTemplate());
        item.setOabpSqlPresetId("custom");
        // bindings=[]: 不绑定任何飞书/本地资料
        item.setDocs(new ArrayList<>());

        String presetJson = "{\"version\":2,\"items\":[]}";

        String merged = PresetAgendaMergeEngine.syncHostAgendaJson(
                MAPPER, 1, presetJson, List.of(item), List.of());

        assertNotNull(merged);
        List<HostAgendaItem> parsed = HostAgendaJsonCodec.parseItems(MAPPER, merged);
        assertEquals(1, parsed.size());
        HostAgendaItem out = parsed.get(0);
        assertEquals("管小慧汇报", out.getTitle());
        assertFalse(out.getOabpTaskSql() == null || out.getOabpTaskSql().isBlank(),
                "oabpTaskSql must survive merge when bindings empty");
        assertEquals(Boolean.TRUE, out.getOabpTaskShow());
        assertNotNull(out.getOabpDisplayTemplate(), "oabpDisplayTemplate must survive merge");
        assertEquals("项目任务", out.getOabpDisplayTemplate().getSheetName());
        assertEquals("custom", out.getOabpSqlPresetId());
        assertTrue(out.getDocs() == null || out.getDocs().isEmpty(), "docs should remain empty");
    }

    @Test
    void enrichHostAgendaItems_fillsOabpFromPresetWhenMissing() {
        // 会议 item 无 oabp 配置
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("项目任务通报");
        item.setMinutes(10);
        // preset 模板项有 oabp
        String presetJson = """
                {"version":2,"items":[
                  {"title":"项目任务通报","minutes":10,
                   "oabpTaskSql":"SELECT task_name AS 待办事项 FROM jq_todos_task",
                   "oabpTaskShow":true,
                   "oabpSqlPresetId":"custom"}
                ]}
                """;

        List<HostAgendaItem> items = new ArrayList<>();
        items.add(item);
        PresetAgendaMergeEngine.enrichHostAgendaItems(1, presetJson, items, List.of());

        HostAgendaItem enriched = items.get(0);
        assertEquals("SELECT task_name AS 待办事项 FROM jq_todos_task", enriched.getOabpTaskSql());
        assertEquals(Boolean.TRUE, enriched.getOabpTaskShow());
        assertEquals("custom", enriched.getOabpSqlPresetId());
    }

    @Test
    void enrichHostAgendaItems_doesNotOverrideExistingOabp() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("项目任务通报");
        item.setMinutes(10);
        item.setOabpTaskSql("SELECT 1 AS n");
        item.setOabpTaskShow(false);
        OabpDisplayTemplate own = sampleTemplate();
        own.setSheetName("我的表");
        item.setOabpDisplayTemplate(own);

        String presetJson = """
                {"version":2,"items":[
                  {"title":"项目任务通报","minutes":10,
                   "oabpTaskSql":"SELECT task_name FROM jq_todos_task",
                   "oabpTaskShow":true}
                ]}
                """;

        List<HostAgendaItem> items = new ArrayList<>();
        items.add(item);
        PresetAgendaMergeEngine.enrichHostAgendaItems(1, presetJson, items, List.of());

        HostAgendaItem out = items.get(0);
        assertEquals("SELECT 1 AS n", out.getOabpTaskSql(), "existing oabpTaskSql must not be overridden");
        assertEquals(Boolean.FALSE, out.getOabpTaskShow(), "existing oabpTaskShow must not be overridden");
        assertEquals("我的表", out.getOabpDisplayTemplate().getSheetName());
    }

    @Test
    void enrichHostAgendaItems_fillsOabpTemplateFromPreset() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("项目任务通报");
        item.setMinutes(10);
        // item 有 SQL 但无 template
        item.setOabpTaskSql("SELECT task_name AS 待办事项 FROM jq_todos_task");

        String presetJson = """
                {"version":2,"items":[
                  {"title":"项目任务通报","minutes":10,
                   "oabpTaskSql":"SELECT task_name AS 待办事项 FROM jq_todos_task",
                   "oabpDisplayTemplate":{"displayMode":"table","sheetName":"项目任务","columns":[{"source":"task_name","label":"待办事项","visible":true}]}}
                ]}
                """;

        List<HostAgendaItem> items = new ArrayList<>();
        items.add(item);
        PresetAgendaMergeEngine.enrichHostAgendaItems(1, presetJson, items, List.of());

        HostAgendaItem out = items.get(0);
        assertNotNull(out.getOabpDisplayTemplate(), "template should be filled from preset");
        assertEquals("项目任务", out.getOabpDisplayTemplate().getSheetName());
    }

    @Test
    void enrichHostAgendaItems_noOabpAnywhere_leavesNull() {
        HostAgendaItem item = new HostAgendaItem();
        item.setTitle("普通会序");
        item.setMinutes(5);
        String presetJson = """
                {"version":2,"items":[{"title":"普通会序","minutes":5}]}
                """;

        List<HostAgendaItem> items = new ArrayList<>();
        items.add(item);
        PresetAgendaMergeEngine.enrichHostAgendaItems(1, presetJson, items, List.of());

        HostAgendaItem out = items.get(0);
        assertNull(out.getOabpTaskSql());
        assertNull(out.getOabpDisplayTemplate());
    }
}
