package com.smartmeeting.config.oabp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OabpSheetTemplateEngineTest {

    @Test
    void apply_emptyTemplate_returnsRaw() {
        OabpSheetData raw = OabpSheetData.builder()
                .headers(List.of("a", "b"))
                .rows(List.of(List.of("1", "2")))
                .build();
        OabpSheetData out = OabpSheetTemplateEngine.apply(raw, null);
        assertEquals(raw.getHeaders(), out.getHeaders());
        assertEquals(raw.getRows(), out.getRows());
    }

    @Test
    void apply_renamesAndFiltersColumns() {
        OabpSheetData raw = OabpSheetData.builder()
                .headers(List.of("status_code", "task_name"))
                .rows(List.of(
                        List.of("0", "A"),
                        List.of("99", "B")))
                .build();

        OabpDisplayColumn col1 = new OabpDisplayColumn();
        col1.setSource("task_name");
        col1.setLabel("任务");
        col1.setVisible(true);
        OabpDisplayColumn col2 = new OabpDisplayColumn();
        col2.setSource("status_code");
        col2.setLabel("状态");
        col2.setVisible(true);
        col2.setFormat("enum");
        col2.setMap(Map.of("0", "进行中", "99", "归档"));

        OabpDisplayFilterNode filter = new OabpDisplayFilterNode();
        filter.setType("rule");
        filter.setField("status_code");
        filter.setOp("!=");
        filter.setValue("99");

        OabpDisplayContent content = new OabpDisplayContent();
        content.setFilter(filter);

        OabpDisplayTemplate template = new OabpDisplayTemplate();
        template.setColumns(List.of(col1, col2));
        template.setContent(content);

        OabpSheetData out = OabpSheetTemplateEngine.apply(raw, template);
        assertEquals(List.of("任务", "状态"), out.getHeaders());
        assertEquals(1, out.getRows().size());
        assertEquals(List.of("A", "进行中"), out.getRows().get(0));
        assertTrue(out.getDisplayMeta() != null);
    }

    @Test
    void apply_preMappedEnumLabel_passesThroughWithoutDefault() {
        OabpSheetData raw = OabpSheetData.builder()
                .headers(List.of("状态", "进度"))
                .rows(List.of(
                        List.of("进行中", "100"),
                        List.of("已完成", "100")))
                .build();
        OabpDisplayColumn col1 = new OabpDisplayColumn();
        col1.setSource("状态");
        col1.setLabel("状态");
        col1.setVisible(true);
        col1.setFormat("enum");
        col1.setMap(Map.of("0", "进行中", "1", "已完成"));
        col1.setDefaultValue("未开始");
        OabpDisplayColumn col2 = new OabpDisplayColumn();
        col2.setSource("进度");
        col2.setLabel("进度");
        col2.setVisible(true);
        OabpDisplayTemplate template = new OabpDisplayTemplate();
        template.setColumns(List.of(col1, col2));

        OabpSheetData out = OabpSheetTemplateEngine.apply(raw, template);
        assertEquals(List.of("进行中", "100"), out.getRows().get(0));
        assertEquals(List.of("已完成", "100"), out.getRows().get(1));
    }

    @Test
    void apply_addsTotalRow() {
        OabpSheetData raw = OabpSheetData.builder()
                .headers(List.of("task_name", "progress"))
                .rows(List.of(
                        List.of("A", "50"),
                        List.of("B", "30")))
                .build();
        OabpDisplayColumn c1 = new OabpDisplayColumn();
        c1.setSource("task_name");
        c1.setLabel("任务");
        c1.setVisible(true);
        OabpDisplayColumn c2 = new OabpDisplayColumn();
        c2.setSource("progress");
        c2.setLabel("进度");
        c2.setVisible(true);
        c2.setFormat("number");
        OabpDisplayTotal total = new OabpDisplayTotal();
        total.setLabel("合计");
        total.setSum(List.of("progress"));
        OabpDisplayTemplate template = new OabpDisplayTemplate();
        template.setColumns(List.of(c1, c2));
        template.setTotals(List.of(total));

        OabpSheetData out = OabpSheetTemplateEngine.apply(raw, template);
        assertEquals(3, out.getRows().size());
        assertEquals("合计", out.getRows().get(2).get(0));
        assertEquals(Integer.valueOf(2), out.getDisplayMeta().getTotalRowIndex());
    }
}
