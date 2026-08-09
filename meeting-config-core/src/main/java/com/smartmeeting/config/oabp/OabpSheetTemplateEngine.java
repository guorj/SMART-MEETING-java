package com.smartmeeting.config.oabp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 oabp 原始 sheet 按 {@link OabpDisplayTemplate} 渲染；模板为空时原样返回。
 */
public final class OabpSheetTemplateEngine {

    private OabpSheetTemplateEngine() {
    }

    public static OabpSheetData apply(OabpSheetData raw, OabpDisplayTemplate template) {
        if (raw == null) {
            return null;
        }
        if (template == null || template.isEmpty()) {
            return raw;
        }
        try {
            return doApply(raw, template);
        } catch (Exception e) {
            return raw;
        }
    }

    private static OabpSheetData doApply(OabpSheetData raw, OabpDisplayTemplate template) {
        List<String> rawHeaders = raw.getHeaders() != null ? raw.getHeaders() : List.of();
        List<List<String>> workingRows = new ArrayList<>();
        if (raw.getRows() != null) {
            for (List<String> row : raw.getRows()) {
                workingRows.add(row != null ? new ArrayList<>(row) : new ArrayList<>());
            }
        }

        OabpDisplayFilterNode filter = template.getContent() != null ? template.getContent().getFilter() : null;
        if (filter != null) {
            workingRows = workingRows.stream()
                    .filter(row -> OabpFilterEvaluator.matches(filter, rawHeaders, row))
                    .toList();
        }

        boolean includeEmpty = template.getContent() != null
                && Boolean.TRUE.equals(template.getContent().getIncludeEmptyRows());
        if (!includeEmpty) {
            workingRows = workingRows.stream().filter(OabpSheetTemplateEngine::notAllEmpty).toList();
        }

        Integer maxRows = template.getContent() != null ? template.getContent().getMaxRows() : null;
        if (maxRows != null && maxRows > 0 && workingRows.size() > maxRows) {
            workingRows = workingRows.subList(0, maxRows);
        }

        List<OabpDisplayColumn> columns = resolveColumns(template, rawHeaders);
        if (columns.isEmpty()) {
            return raw;
        }

        if (template.getSort() != null && !template.getSort().isEmpty()) {
            workingRows = sortRows(workingRows, rawHeaders, template.getSort());
        }

        List<String> outHeaders = new ArrayList<>();
        List<Integer> outWidths = new ArrayList<>();
        List<String> renderAsList = new ArrayList<>();
        for (OabpDisplayColumn col : columns) {
            outHeaders.add(labelOf(col));
            renderAsList.add(col.getRenderAs() != null ? col.getRenderAs() : "plain");
            outWidths.add(col.getWidth());
        }

        List<List<String>> outRows = new ArrayList<>();
        for (List<String> row : workingRows) {
            List<String> out = new ArrayList<>();
            for (OabpDisplayColumn col : columns) {
                String rawVal = cellBySource(rawHeaders, row, col.getSource());
                out.add(formatCell(rawVal, col));
            }
            outRows.add(out);
        }

        Integer totalRowIndex = null;
        if (template.getTotals() != null && !template.getTotals().isEmpty()) {
            OabpDisplayTotal totalSpec = template.getTotals().get(0);
            if (totalSpec != null && totalSpec.getSum() != null && !totalSpec.getSum().isEmpty()) {
                outRows.add(buildTotalRow(totalSpec, columns, rawHeaders, workingRows));
                totalRowIndex = outRows.size() - 1;
            }
        }

        OabpSheetDisplayMeta meta = buildDisplayMeta(
                template, rawHeaders, workingRows, outHeaders, renderAsList, totalRowIndex);

        return OabpSheetData.builder()
                .sheetName(template.getSheetName() != null && !template.getSheetName().isBlank()
                        ? template.getSheetName().trim()
                        : raw.getSheetName())
                .headers(outHeaders)
                .rows(outRows)
                .columnWidths(outWidths)
                .headerRowCount(Math.max(1, raw.getHeaderRowCount()))
                .displayMeta(meta)
                .build();
    }

    private static List<OabpDisplayColumn> resolveColumns(OabpDisplayTemplate template, List<String> rawHeaders) {
        List<OabpDisplayColumn> cols = new ArrayList<>();
        if (template.getColumns() != null && !template.getColumns().isEmpty()) {
            for (OabpDisplayColumn c : template.getColumns()) {
                if (c != null && c.isVisible() && c.getSource() != null && !c.getSource().isBlank()) {
                    cols.add(c);
                }
            }
            return cols;
        }
        for (String h : rawHeaders) {
            OabpDisplayColumn c = new OabpDisplayColumn();
            c.setSource(h);
            c.setLabel(h);
            c.setVisible(true);
            cols.add(c);
        }
        return cols;
    }

    private static OabpSheetDisplayMeta buildDisplayMeta(
            OabpDisplayTemplate template,
            List<String> rawHeaders,
            List<List<String>> rowsBeforeProject,
            List<String> outHeaders,
            List<String> renderAsList,
            Integer totalRowIndex) {
        String mode = template.getDisplayMode() != null ? template.getDisplayMode().trim() : "table";
        OabpSheetDisplayMeta.OabpSheetDisplayMetaBuilder builder = OabpSheetDisplayMeta.builder()
                .displayMode(mode)
                .columnRenderAs(renderAsList)
                .totalRowIndex(totalRowIndex);

        if (!"grouped_table".equalsIgnoreCase(mode) || template.getGroupBy() == null) {
            return builder.build();
        }

        OabpDisplayGroupBy gb = template.getGroupBy();
        String source = gb.getSource();
        if (source == null || source.isBlank()) {
            return builder.build();
        }

        Map<String, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < rowsBeforeProject.size(); i++) {
            String key = cellBySource(rawHeaders, rowsBeforeProject.get(i), source.trim());
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }

        List<String> order = gb.getOrder() != null ? gb.getOrder() : List.of();
        List<OabpSheetGroupMeta> groups = new ArrayList<>();
        for (String key : order) {
            appendGroup(groups, gb, key, buckets.remove(key));
        }
        for (Map.Entry<String, List<Integer>> e : buckets.entrySet()) {
            appendGroup(groups, gb, e.getKey(), e.getValue());
        }
        return builder.groups(groups).build();
    }

    private static void appendGroup(
            List<OabpSheetGroupMeta> groups, OabpDisplayGroupBy gb, String key, List<Integer> indices) {
        if (indices == null || indices.isEmpty()) {
            return;
        }
        String title = resolveGroupTitle(gb, key);
        if (Boolean.TRUE.equals(gb.getShowCount())) {
            title = title + " (" + indices.size() + ")";
        }
        int start = indices.get(0);
        groups.add(OabpSheetGroupMeta.builder()
                .title(title)
                .startRow(start)
                .rowCount(indices.size())
                .build());
    }

    private static String resolveGroupTitle(OabpDisplayGroupBy gb, String key) {
        if (gb.getMap() != null && gb.getMap().containsKey(key)) {
            return gb.getMap().get(key);
        }
        if (key == null || key.isBlank()) {
            return "未分组";
        }
        return key;
    }

    private static List<List<String>> sortRows(
            List<List<String>> rows, List<String> headers, List<OabpDisplaySort> sortSpecs) {
        List<List<String>> sorted = new ArrayList<>(rows);
        Comparator<List<String>> cmp = null;
        for (OabpDisplaySort spec : sortSpecs) {
            if (spec == null || spec.getBy() == null || spec.getBy().isBlank()) {
                continue;
            }
            boolean desc = "desc".equalsIgnoreCase(spec.getDir());
            Comparator<List<String>> c = (a, b) -> {
                String va = cellBySource(headers, a, spec.getBy().trim());
                String vb = cellBySource(headers, b, spec.getBy().trim());
                int r = va.compareTo(vb);
                return desc ? -r : r;
            };
            cmp = cmp == null ? c : cmp.thenComparing(c);
        }
        if (cmp != null) {
            sorted.sort(cmp);
        }
        return sorted;
    }

    private static String labelOf(OabpDisplayColumn col) {
        if (col.getLabel() != null && !col.getLabel().isBlank()) {
            return col.getLabel().trim();
        }
        return col.getSource();
    }

    private static String cellBySource(List<String> headers, List<String> row, String source) {
        if (headers == null || row == null || source == null) {
            return "";
        }
        for (int i = 0; i < headers.size(); i++) {
            if (source.equals(headers.get(i))) {
                return i < row.size() && row.get(i) != null ? row.get(i) : "";
            }
        }
        return "";
    }

    private static String formatCell(String raw, OabpDisplayColumn col) {
        if (raw == null) {
            raw = "";
        }
        String format = col.getFormat() != null ? col.getFormat().trim().toLowerCase(Locale.ROOT) : "plain";
        return switch (format) {
            case "enum" -> mapEnum(raw, col);
            case "percent" -> formatPercent(raw);
            case "date", "datetime" -> raw.length() >= 10 ? raw.substring(0, 10) : raw;
            case "number" -> raw;
            default -> raw;
        };
    }

    private static String mapEnum(String raw, OabpDisplayColumn col) {
        Map<String, String> map = col.getMap();
        if (map != null && map.containsKey(raw)) {
            return map.get(raw);
        }
        if ((raw == null || raw.isBlank())
                && col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
            return col.getDefaultValue();
        }
        return raw != null ? raw : "";
    }

    private static String formatPercent(String raw) {
        if (raw.isBlank()) {
            return raw;
        }
        try {
            double n = Double.parseDouble(raw.replace("%", "").trim());
            if (n <= 1.0 && n >= 0 && !raw.contains("%")) {
                n *= 100;
            }
            return Math.round(n) + "%";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private static boolean notAllEmpty(List<String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        for (String c : row) {
            if (c != null && !c.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> buildTotalRow(
            OabpDisplayTotal spec,
            List<OabpDisplayColumn> columns,
            List<String> rawHeaders,
            List<List<String>> rawRows) {
        List<String> sumFields = spec.getSum() != null ? spec.getSum() : List.of();
        List<String> row = new ArrayList<>();
        boolean labelPlaced = false;
        for (OabpDisplayColumn col : columns) {
            if (!labelPlaced) {
                row.add(spec.getLabel() != null && !spec.getLabel().isBlank() ? spec.getLabel().trim() : "合计");
                labelPlaced = true;
            } else if (sumFields.contains(col.getSource())) {
                row.add(formatCell(String.valueOf(Math.round(sumRawColumn(rawHeaders, rawRows, col.getSource()))), col));
            } else {
                row.add("");
            }
        }
        return row;
    }

    private static double sumRawColumn(List<String> headers, List<List<String>> rows, String source) {
        double sum = 0;
        for (List<String> row : rows) {
            String raw = cellBySource(headers, row, source);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                sum += Double.parseDouble(raw.replace("%", "").trim());
            } catch (NumberFormatException ignored) {
                // skip non-numeric
            }
        }
        return sum;
    }
}
