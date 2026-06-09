package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 将多维表格记录导出为 plainText（含数据表 / 近三个月 / 更早分区标题）。
 */
public final class BitablePlainTextExporter {

    public static final String SECTION_RECENT = "=== §近三个月（按创建日期）===";
    public static final String SECTION_OLDER = "=== §三个月以前 ===";
    public static final String TABLE_SECTION_PREFIX = "=== §数据表：";

    public static final String SECTION_STATUS_COMPLETED = "=== §已完成 ===";
    public static final String SECTION_STATUS_DELAYED = "=== §延期 ===";
    public static final String SECTION_STATUS_IN_PROGRESS = "=== §进行中 ===";
    public static final String EMPTY_STATUS_CATEGORY = "（本分类暂无记录）";

    /** 近三个月内状态子段导出顺序：延期 → 已完成 → 进行中。 */
    private static final int[] STATUS_SECTION_CATEGORIES = {
            BitableRecordSorter.CATEGORY_DELAYED,
            BitableRecordSorter.CATEGORY_COMPLETED,
            BitableRecordSorter.CATEGORY_IN_PROGRESS
    };
    private static final String[] STATUS_SECTION_HEADERS = {
            SECTION_STATUS_DELAYED,
            SECTION_STATUS_COMPLETED,
            SECTION_STATUS_IN_PROGRESS
    };

    private BitablePlainTextExporter() {
    }

    public static String tableSectionHeader(String tableName) {
        String name = tableName == null || tableName.isBlank() ? "未命名" : tableName.trim();
        return TABLE_SECTION_PREFIX + name + " ===";
    }

    public static String export(List<JsonNode> items, String titleSummary) {
        return export(items, titleSummary, BitableDisplayMode.GROUPED);
    }

    public static String export(List<JsonNode> items, String titleSummary, BitableDisplayMode mode) {
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        StringBuilder out = new StringBuilder();
        out.append(titleSummary).append("\n\n");
        if (items == null || items.isEmpty()) {
            out.append("（无记录）");
            return out.toString().trim();
        }
        if (effective == BitableDisplayMode.RAW) {
            appendFlatRecords(out, items, 0);
        } else {
            appendGroupedRecords(out, items);
        }
        return out.toString().trim();
    }

    /**
     * 多数据表（多 sheet）合并导出：每个表独立分区，表内按 mode 导出。
     */
    public static String exportMultiTable(List<BitableTableSlice> tables, String titleSummary) {
        return exportMultiTable(tables, titleSummary, BitableDisplayMode.GROUPED);
    }

    public static String exportMultiTable(List<BitableTableSlice> tables, String titleSummary,
            BitableDisplayMode mode) {
        if (tables == null || tables.isEmpty()) {
            return "（无数据表）";
        }
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        StringBuilder out = new StringBuilder();
        out.append(titleSummary).append("\n\n");
        boolean firstTable = true;
        int row = 0;
        for (BitableTableSlice slice : tables) {
            if (!firstTable) {
                out.append('\n');
            }
            firstTable = false;
            out.append(tableSectionHeader(slice.tableName())).append("\n\n");
            List<JsonNode> items = slice.items() != null ? slice.items() : List.of();
            if (items.isEmpty()) {
                out.append("（本表无记录）\n");
                continue;
            }
            if (effective == BitableDisplayMode.RAW) {
                row = appendFlatRecords(out, items, row);
            } else {
                row = appendGroupedRecords(out, items, row);
            }
        }
        return out.toString().trim();
    }

    private static void appendGroupedRecords(StringBuilder out, List<JsonNode> items) {
        appendGroupedRecords(out, items, 0);
    }

    private static int appendGroupedRecords(StringBuilder out, List<JsonNode> items, int rowStart) {
        List<JsonNode> sorted = BitableRecordSorter.sortedCopy(items);
        List<JsonNode> recent = new ArrayList<>();
        List<JsonNode> older = new ArrayList<>();
        for (JsonNode item : sorted) {
            if (BitableRecordSorter.recentBucket(item) == 0) {
                recent.add(item);
            } else {
                older.add(item);
            }
        }
        int row = rowStart;
        if (!recent.isEmpty()) {
            out.append(SECTION_RECENT).append("\n\n");
            row = appendRecentWithStatusGroups(out, recent, row);
        }
        if (!older.isEmpty()) {
            if (!recent.isEmpty()) {
                out.append('\n');
            }
            out.append(SECTION_OLDER).append("\n\n");
            row = appendRecordList(out, older, row);
        }
        return row;
    }

    private static int appendFlatRecords(StringBuilder out, List<JsonNode> items, int rowStart) {
        return appendRecordList(out, items, rowStart);
    }

    private static int appendRecentWithStatusGroups(StringBuilder out, List<JsonNode> recent, int rowStart) {
        int row = rowStart;
        for (int i = 0; i < STATUS_SECTION_CATEGORIES.length; i++) {
            int category = STATUS_SECTION_CATEGORIES[i];
            String header = STATUS_SECTION_HEADERS[i];
            if (i > 0) {
                out.append('\n');
            }
            out.append(header).append("\n\n");
            List<JsonNode> inCategory = new ArrayList<>();
            for (JsonNode item : recent) {
                if (BitableRecordSorter.displayCategory(item) == category) {
                    inCategory.add(item);
                }
            }
            if (inCategory.isEmpty()) {
                out.append(EMPTY_STATUS_CATEGORY).append("\n\n");
            } else {
                row = appendRecordList(out, inCategory, row);
            }
        }
        return row;
    }

    private static int appendRecordList(StringBuilder out, List<JsonNode> items, int rowStart) {
        int row = rowStart;
        for (JsonNode item : items) {
            row++;
            out.append("--- 记录 ").append(row).append(" ---\n");
            JsonNode fields = item.path("fields");
            if (fields.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String fieldName = e.getKey();
                    appendFieldLine(out, fieldName, BitableFieldFormatter.format(e.getValue(), fieldName));
                }
            }
            out.append('\n');
        }
        return row;
    }

    /**
     * 导出单字段行；值内换行用两空格缩进续行，供前端 parseBitableRecordBlocks 合并。
     */
    static void appendFieldLine(StringBuilder out, String fieldName, String value) {
        String name = fieldName == null ? "" : fieldName;
        String v = value == null ? "" : value.replace("\r\n", "\n");
        if (!v.contains("\n")) {
            out.append(name).append(": ").append(v).append('\n');
            return;
        }
        String[] lines = v.split("\n", -1);
        out.append(name).append(": ").append(lines[0]).append('\n');
        for (int i = 1; i < lines.length; i++) {
            out.append("  ").append(lines[i]).append('\n');
        }
    }

    /** 单表记录切片，供多 sheet 合并。 */
    public record BitableTableSlice(String tableId, String tableName, List<JsonNode> items) {
    }
}
