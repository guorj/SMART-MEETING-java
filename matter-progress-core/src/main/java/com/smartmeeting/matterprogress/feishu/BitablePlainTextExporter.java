package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 将已排序的多维表格记录导出为 plainText（含数据表 / 近三个月 / 更早分区标题）。
 */
public final class BitablePlainTextExporter {

    public static final String SECTION_RECENT = "=== §近三个月（按创建日期）===";
    public static final String SECTION_OLDER = "=== §三个月以前 ===";
    public static final String TABLE_SECTION_PREFIX = "=== §数据表：";

    private BitablePlainTextExporter() {
    }

    public static String tableSectionHeader(String tableName) {
        String name = tableName == null || tableName.isBlank() ? "未命名" : tableName.trim();
        return TABLE_SECTION_PREFIX + name + " ===";
    }

    public static String export(List<JsonNode> items, String titleSummary) {
        BitableRecordSorter.sort(items);
        StringBuilder out = new StringBuilder();
        out.append(titleSummary).append("\n\n");
        if (items.isEmpty()) {
            out.append("（无记录）");
            return out.toString().trim();
        }
        appendSortedRecords(out, items);
        return out.toString().trim();
    }

    /**
     * 多数据表（多 sheet）合并导出：每个表独立分区，表内仍按近 3 个月 / 状态规则排序。
     */
    public static String exportMultiTable(List<BitableTableSlice> tables, String titleSummary) {
        if (tables == null || tables.isEmpty()) {
            return "（无数据表）";
        }
        StringBuilder out = new StringBuilder();
        out.append(titleSummary).append("\n\n");
        boolean firstTable = true;
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
            appendSortedRecords(out, items);
        }
        return out.toString().trim();
    }

    private static void appendSortedRecords(StringBuilder out, List<JsonNode> items) {
        BitableRecordSorter.sort(items);
        int lastBucket = -1;
        int row = 0;
        for (JsonNode item : items) {
            int bucket = BitableRecordSorter.recentBucket(item);
            if (bucket != lastBucket) {
                if (lastBucket >= 0) {
                    out.append('\n');
                }
                out.append(bucket == 0 ? SECTION_RECENT : SECTION_OLDER).append("\n\n");
                lastBucket = bucket;
            }
            row++;
            out.append("--- 记录 ").append(row).append(" ---\n");
            JsonNode fields = item.path("fields");
            if (fields.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String fieldName = e.getKey();
                    out.append(fieldName).append(": ")
                            .append(BitableFieldFormatter.format(e.getValue(), fieldName)).append('\n');
                }
            }
            out.append('\n');
        }
    }

    /** 单表记录切片，供多 sheet 合并。 */
    public record BitableTableSlice(String tableId, String tableName, List<JsonNode> items) {
    }
}
