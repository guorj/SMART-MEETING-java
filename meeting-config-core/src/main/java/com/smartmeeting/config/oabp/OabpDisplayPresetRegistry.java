package com.smartmeeting.config.oabp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 内置展示模板预设注册表。 */
public final class OabpDisplayPresetRegistry {

    private static final Map<String, OabpDisplayPreset> PRESETS = new LinkedHashMap<>();

    static {
        register(defaultTable());
        register(projectTaskGrouped());
        register(minimalProgress());
    }

    private OabpDisplayPresetRegistry() {
    }

    private static void register(OabpDisplayPreset preset) {
        PRESETS.put(preset.getId(), preset);
    }

    public static List<OabpDisplayPreset> list() {
        return List.copyOf(PRESETS.values());
    }

    public static Optional<OabpDisplayPreset> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PRESETS.get(id.trim()));
    }

    /** 深拷贝模板，避免调用方修改内置预设。 */
    public static OabpDisplayTemplate copyTemplate(String id) {
        return find(id).map(p -> cloneTemplate(p.getTemplate())).orElse(null);
    }

    private static OabpDisplayPreset defaultTable() {
        List<OabpDisplayColumn> cols = new ArrayList<>();
        cols.add(column("task_name", "待办事项", "long_text", "plain", null));
        cols.add(column("status_code", "状态", "badge", "enum", statusMap()));
        cols.add(column("progress", "进度", "progress_bar", "percent", null));
        cols.add(column("plan_date", "截止日期", "plain", "date", null));

        OabpDisplayTemplate t = baseTemplate("table", "项目任务", cols);
        return OabpDisplayPreset.builder()
                .id("default_table")
                .name("平铺表格")
                .description("全列平铺展示，适合通用场景")
                .template(t)
                .build();
    }

    private static OabpDisplayPreset projectTaskGrouped() {
        List<OabpDisplayColumn> cols = new ArrayList<>();
        cols.add(column("task_name", "待办事项", "long_text", "plain", null));
        cols.add(column("status_code", "状态", "badge", "enum", statusMap()));
        cols.add(column("progress", "进度", "progress_bar", "percent", null));
        cols.add(column("plan_date", "截止日期", "plain", "date", null));

        OabpDisplayFilterNode filter = new OabpDisplayFilterNode();
        filter.setType("rule");
        filter.setField("status_code");
        filter.setOp("!=");
        filter.setValue("99");

        OabpDisplayContent content = new OabpDisplayContent();
        content.setFilter(filter);
        content.setIncludeEmptyRows(false);
        content.setMaxRows(200);

        OabpDisplaySort sort = new OabpDisplaySort();
        sort.setBy("plan_date");
        sort.setDir("asc");

        OabpDisplayGroupBy groupBy = new OabpDisplayGroupBy();
        groupBy.setSource("status_code");
        groupBy.setLabel("状态");
        groupBy.setMap(statusMap());
        groupBy.setOrder(List.of("2", "0", "1"));
        groupBy.setShowCount(true);

        OabpDisplayTemplate t = baseTemplate("grouped_table", "项目任务", cols);
        t.setContent(content);
        t.setSort(List.of(sort));
        t.setGroupBy(groupBy);

        return OabpDisplayPreset.builder()
                .id("project_task_grouped")
                .name("项目任务（按状态分组）")
                .description("按进行中/已逾期/已完成分组，替代主持页硬编码分组逻辑")
                .template(t)
                .build();
    }

    private static OabpDisplayPreset minimalProgress() {
        List<OabpDisplayColumn> cols = new ArrayList<>();
        cols.add(column("task_name", "待办事项", "long_text", "plain", null));
        cols.add(column("progress", "进度", "progress_bar", "percent", null));
        cols.add(column("plan_date", "截止日期", "plain", "date", null));

        OabpDisplayTemplate t = baseTemplate("table", "项目进度", cols);
        return OabpDisplayPreset.builder()
                .id("minimal_progress")
                .name("精简进度表")
                .description("仅任务名、进度与截止日期")
                .template(t)
                .build();
    }

    private static OabpDisplayTemplate baseTemplate(String mode, String sheetName, List<OabpDisplayColumn> cols) {
        OabpDisplayTemplate t = new OabpDisplayTemplate();
        t.setVersion(1);
        t.setDisplayMode(mode);
        t.setSheetName(sheetName);
        t.setColumns(cols);
        return t;
    }

    private static OabpDisplayColumn column(
            String source, String label, String renderAs, String format, Map<String, String> map) {
        OabpDisplayColumn c = new OabpDisplayColumn();
        c.setSource(source);
        c.setLabel(label);
        c.setVisible(true);
        c.setFormat(format);
        c.setRenderAs(renderAs);
        if (map != null) {
            c.setMap(new LinkedHashMap<>(map));
        }
        if ("enum".equals(format)) {
            c.setDefaultValue("未开始");
        }
        return c;
    }

    private static Map<String, String> statusMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("0", "进行中");
        map.put("1", "已完成");
        map.put("2", "已逾期");
        map.put("99", "归档");
        return map;
    }

    private static OabpDisplayTemplate cloneTemplate(OabpDisplayTemplate src) {
        if (src == null) {
            return null;
        }
        OabpDisplayTemplate t = new OabpDisplayTemplate();
        t.setVersion(src.getVersion());
        t.setDisplayMode(src.getDisplayMode());
        t.setSheetName(src.getSheetName());
        if (src.getColumns() != null) {
            List<OabpDisplayColumn> cols = new ArrayList<>();
            for (OabpDisplayColumn c : src.getColumns()) {
                if (c == null) {
                    continue;
                }
                OabpDisplayColumn copy = new OabpDisplayColumn();
                copy.setSource(c.getSource());
                copy.setLabel(c.getLabel());
                copy.setVisible(c.getVisible());
                copy.setWidth(c.getWidth());
                copy.setAlign(c.getAlign());
                copy.setFormat(c.getFormat());
                copy.setRenderAs(c.getRenderAs());
                if (c.getMap() != null) {
                    copy.setMap(new LinkedHashMap<>(c.getMap()));
                }
                copy.setDefaultValue(c.getDefaultValue());
                cols.add(copy);
            }
            t.setColumns(cols);
        }
        if (src.getContent() != null) {
            OabpDisplayContent content = new OabpDisplayContent();
            content.setFilter(cloneFilter(src.getContent().getFilter()));
            content.setIncludeEmptyRows(src.getContent().getIncludeEmptyRows());
            content.setMaxRows(src.getContent().getMaxRows());
            t.setContent(content);
        }
        if (src.getSort() != null) {
            List<OabpDisplaySort> sorts = new ArrayList<>();
            for (OabpDisplaySort s : src.getSort()) {
                if (s == null) {
                    continue;
                }
                OabpDisplaySort copy = new OabpDisplaySort();
                copy.setBy(s.getBy());
                copy.setDir(s.getDir());
                sorts.add(copy);
            }
            t.setSort(sorts);
        }
        if (src.getGroupBy() != null) {
            OabpDisplayGroupBy gb = new OabpDisplayGroupBy();
            gb.setSource(src.getGroupBy().getSource());
            gb.setLabel(src.getGroupBy().getLabel());
            if (src.getGroupBy().getMap() != null) {
                gb.setMap(new LinkedHashMap<>(src.getGroupBy().getMap()));
            }
            if (src.getGroupBy().getOrder() != null) {
                gb.setOrder(new ArrayList<>(src.getGroupBy().getOrder()));
            }
            gb.setShowCount(src.getGroupBy().getShowCount());
            t.setGroupBy(gb);
        }
        return t;
    }

    private static OabpDisplayFilterNode cloneFilter(OabpDisplayFilterNode node) {
        if (node == null) {
            return null;
        }
        OabpDisplayFilterNode copy = new OabpDisplayFilterNode();
        copy.setType(node.getType());
        copy.setOp(node.getOp());
        copy.setField(node.getField());
        copy.setValue(node.getValue());
        if (node.getChildren() != null) {
            List<OabpDisplayFilterNode> children = new ArrayList<>();
            for (OabpDisplayFilterNode child : node.getChildren()) {
                children.add(cloneFilter(child));
            }
            copy.setChildren(children);
        }
        return copy;
    }
}
