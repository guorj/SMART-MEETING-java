package com.smartmeeting.config.oabp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 展示模板配置校验（admin 保存/预览前）。 */
public final class OabpDisplayTemplateValidator {

    private static final Set<String> DISPLAY_MODES = Set.of("table", "grouped_table");
    private static final Set<String> FORMATS = Set.of("plain", "date", "datetime", "percent", "number", "enum");
    private static final Set<String> RENDER_AS = Set.of("plain", "badge", "progress_bar", "long_text", "numeric");
    private static final Set<String> SORT_DIR = Set.of("asc", "desc");
    private static final Set<String> GROUP_OPS = Set.of("and", "or", "not");

    private OabpDisplayTemplateValidator() {
    }

    public static List<String> validate(OabpDisplayTemplate template, List<String> knownHeaders, int maxQueryRows) {
        List<String> issues = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return issues;
        }
        if (template.getDisplayMode() != null && !template.getDisplayMode().isBlank()
                && !DISPLAY_MODES.contains(template.getDisplayMode().trim().toLowerCase(Locale.ROOT))) {
            issues.add("displayMode 非法: " + template.getDisplayMode());
        }
        Set<String> sources = new HashSet<>();
        if (template.getColumns() != null) {
            for (int i = 0; i < template.getColumns().size(); i++) {
                OabpDisplayColumn col = template.getColumns().get(i);
                if (col == null) {
                    continue;
                }
                String src = col.getSource() != null ? col.getSource().trim() : "";
                if (src.isEmpty()) {
                    issues.add("columns[" + i + "].source 不能为空");
                    continue;
                }
                if (!sources.add(src)) {
                    issues.add("columns source 重复: " + src);
                }
                if (knownHeaders != null && !knownHeaders.isEmpty() && !knownHeaders.contains(src)) {
                    issues.add("columns source 不在 SQL 结果列中: " + src);
                }
                if (col.getFormat() != null && !col.getFormat().isBlank()
                        && !FORMATS.contains(col.getFormat().trim().toLowerCase(Locale.ROOT))) {
                    issues.add("columns[" + i + "].format 非法: " + col.getFormat());
                }
                if (col.getRenderAs() != null && !col.getRenderAs().isBlank()
                        && !RENDER_AS.contains(col.getRenderAs().trim().toLowerCase(Locale.ROOT))) {
                    issues.add("columns[" + i + "].renderAs 非法: " + col.getRenderAs());
                }
            }
        }
        if (template.getContent() != null && template.getContent().getMaxRows() != null) {
            int max = template.getContent().getMaxRows();
            if (max <= 0) {
                issues.add("content.maxRows 须为正整数");
            } else if (maxQueryRows > 0 && max > maxQueryRows) {
                issues.add("content.maxRows 不能超过 " + maxQueryRows);
            }
        }
        if (template.getContent() != null && template.getContent().getFilter() != null) {
            issues.addAll(validateFilterNode(template.getContent().getFilter(), knownHeaders, "filter", 0));
        }
        if (template.getSort() != null) {
            for (int i = 0; i < template.getSort().size(); i++) {
                OabpDisplaySort s = template.getSort().get(i);
                if (s == null || s.getBy() == null || s.getBy().isBlank()) {
                    issues.add("sort[" + i + "].by 不能为空");
                }
                if (s != null && s.getDir() != null && !s.getDir().isBlank()
                        && !SORT_DIR.contains(s.getDir().trim().toLowerCase(Locale.ROOT))) {
                    issues.add("sort[" + i + "].dir 非法");
                }
            }
        }
        if (template.getGroupBy() != null && template.getGroupBy().getSource() != null
                && knownHeaders != null && !knownHeaders.isEmpty()
                && !knownHeaders.contains(template.getGroupBy().getSource().trim())) {
            issues.add("groupBy.source 不在 SQL 结果列中: " + template.getGroupBy().getSource());
        }
        if (template.getTotals() != null) {
            for (int i = 0; i < template.getTotals().size(); i++) {
                OabpDisplayTotal total = template.getTotals().get(i);
                if (total == null || total.getSum() == null) {
                    continue;
                }
                for (String field : total.getSum()) {
                    if (field != null && !field.isBlank() && knownHeaders != null && !knownHeaders.isEmpty()
                            && !knownHeaders.contains(field.trim()) && !sources.contains(field.trim())) {
                        issues.add("totals[" + i + "].sum 字段不在列中: " + field);
                    }
                }
            }
        }
        return issues;
    }

    private static List<String> validateFilterNode(
            OabpDisplayFilterNode node, List<String> knownHeaders, String path, int depth) {
        List<String> issues = new ArrayList<>();
        if (node == null) {
            return issues;
        }
        if (depth > 4) {
            issues.add(path + ": 嵌套深度超过 4");
            return issues;
        }
        String type = node.getType() != null ? node.getType().trim().toLowerCase(Locale.ROOT) : "rule";
        if ("group".equals(type)) {
            String op = node.getOp() != null ? node.getOp().trim().toLowerCase(Locale.ROOT) : "and";
            if (!GROUP_OPS.contains(op)) {
                issues.add(path + ": group op 非法");
            }
            if (node.getChildren() != null) {
                for (int i = 0; i < node.getChildren().size(); i++) {
                    issues.addAll(validateFilterNode(
                            node.getChildren().get(i), knownHeaders, path + ".children[" + i + "]", depth + 1));
                }
            }
            return issues;
        }
        String field = node.getField() != null ? node.getField().trim() : "";
        if (field.isEmpty()) {
            issues.add(path + ": rule.field 不能为空");
        } else if (knownHeaders != null && !knownHeaders.isEmpty() && !knownHeaders.contains(field)) {
            issues.add(path + ": field 不在 SQL 结果列中: " + field);
        }
        return issues;
    }
}
