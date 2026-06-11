package com.smartmeeting.service.structured;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.*;
import com.smartmeeting.matterprogress.feishu.BitableDisplayMode;
import com.smartmeeting.matterprogress.feishu.BitableFieldFormatter;
import com.smartmeeting.matterprogress.feishu.BitablePlainTextExporter;
import com.smartmeeting.matterprogress.feishu.BitableRecordSorter;

import java.util.*;

public final class BitableStructuredExporter {
    private static final String[] STATUS_GROUP_LABELS = {
            BitablePlainTextExporter.SECTION_STATUS_DELAYED,
            BitablePlainTextExporter.SECTION_STATUS_COMPLETED,
            BitablePlainTextExporter.SECTION_STATUS_IN_PROGRESS
    };
    private static final int[] STATUS_GROUP_CATEGORIES = {
            BitableRecordSorter.CATEGORY_DELAYED,
            BitableRecordSorter.CATEGORY_COMPLETED,
            BitableRecordSorter.CATEGORY_IN_PROGRESS
    };

    private BitableStructuredExporter() {}

    public static BitableStructuredDto export(List<JsonNode> items, String tableName) {
        return export(items, tableName, BitableDisplayMode.GROUPED);
    }

    public static BitableStructuredDto export(List<JsonNode> items, String tableName, BitableDisplayMode mode) {
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        if (items == null || items.isEmpty()) {
            return BitableStructuredDto.builder()
                    .tableName(tableName)
                    .columns(List.of())
                    .records(List.of())
                    .groups(List.of())
                    .totalRecords(0)
                    .build();
        }
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (JsonNode item : items) {
            collectFieldTypes(item.path("fields"), fieldTypes);
        }
        List<BitableColumnDto> columns = fieldTypes.entrySet().stream()
                .map(e -> BitableColumnDto.builder().name(e.getKey()).type(e.getValue()).build())
                .toList();
        List<BitableRecordDto> records = toRecordDtos(items);
        List<BitableGroupDto> groups = effective == BitableDisplayMode.GROUPED
                ? buildGroups(items) : List.of();
        return BitableStructuredDto.builder()
                .tableName(tableName)
                .columns(columns)
                .records(records)
                .groups(groups)
                .totalRecords(records.size())
                .build();
    }

    public static BitableStructuredDto exportMultiTable(List<BitablePlainTextExporter.BitableTableSlice> tables,
                                                        BitableDisplayMode mode) {
        if (tables == null || tables.isEmpty()) {
            return BitableStructuredDto.builder()
                    .tableName("无数据表")
                    .columns(List.of())
                    .records(List.of())
                    .groups(List.of())
                    .tables(List.of())
                    .totalRecords(0)
                    .build();
        }
        List<BitableStructuredDto> tableDtos = new ArrayList<>();
        int total = 0;
        for (BitablePlainTextExporter.BitableTableSlice slice : tables) {
            List<JsonNode> items = slice.items() != null ? slice.items() : List.of();
            BitableStructuredDto one = export(items, slice.tableName(), mode);
            tableDtos.add(one);
            total += one.getTotalRecords();
        }
        BitableStructuredDto first = tableDtos.get(0);
        return BitableStructuredDto.builder()
                .tableName(tables.size() + " 个数据表")
                .columns(first.getColumns())
                .records(first.getRecords())
                .groups(first.getGroups())
                .tables(tableDtos)
                .totalRecords(total)
                .build();
    }

    private static void collectFieldTypes(JsonNode fields, Map<String, String> fieldTypes) {
        if (fields == null || !fields.isObject()) {
            return;
        }
        var it = fields.fields();
        while (it.hasNext()) {
            var e = it.next();
            fieldTypes.putIfAbsent(e.getKey(), guessFieldType(e.getValue()));
        }
    }

    private static List<BitableRecordDto> toRecordDtos(List<JsonNode> items) {
        List<JsonNode> sorted = BitableRecordSorter.sortedCopy(items);
        List<BitableRecordDto> records = new ArrayList<>();
        for (JsonNode item : sorted) {
            Map<String, Object> fieldValues = new LinkedHashMap<>();
            JsonNode fields = item.path("fields");
            if (fields.isObject()) {
                var it = fields.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    fieldValues.put(e.getKey(), formatFieldValue(e.getValue(), e.getKey()));
                }
            }
            records.add(BitableRecordDto.builder()
                    .recordId(item.path("record_id").asText(null))
                    .fields(fieldValues)
                    .build());
        }
        return records;
    }

    private static Object formatFieldValue(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isNumber()) {
            double n = value.asDouble();
            if (fieldName != null && (fieldName.contains("进度") || fieldName.toLowerCase(Locale.ROOT).contains("progress"))) {
                return n;
            }
            return n;
        }
        if (value.isArray() && !value.isEmpty() && value.get(0).has("name")) {
            List<String> names = new ArrayList<>();
            for (JsonNode p : value) {
                names.add(p.path("name").asText(""));
            }
            return String.join("、", names);
        }
        return BitableFieldFormatter.format(value, fieldName);
    }

    private static List<BitableGroupDto> buildGroups(List<JsonNode> items) {
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
        List<BitableGroupDto> groups = new ArrayList<>();
        if (!recent.isEmpty()) {
            groups.add(BitableGroupDto.builder()
                    .field("_section")
                    .value(BitablePlainTextExporter.SECTION_RECENT)
                    .records(List.of())
                    .build());
            for (int i = 0; i < STATUS_GROUP_CATEGORIES.length; i++) {
                int category = STATUS_GROUP_CATEGORIES[i];
                List<JsonNode> inCategory = new ArrayList<>();
                for (JsonNode item : recent) {
                    if (BitableRecordSorter.displayCategory(item) == category) {
                        inCategory.add(item);
                    }
                }
                groups.add(BitableGroupDto.builder()
                        .field("_section")
                        .value(STATUS_GROUP_LABELS[i])
                        .records(toRecordDtos(inCategory))
                        .build());
            }
        }
        if (!older.isEmpty()) {
            groups.add(BitableGroupDto.builder()
                    .field("_section")
                    .value(BitablePlainTextExporter.SECTION_OLDER)
                    .records(toRecordDtos(older))
                    .build());
        }
        return groups;
    }

    private static String guessFieldType(JsonNode value) {
        if (value == null || value.isNull()) return "text";
        if (value.isNumber()) return "number";
        if (value.isBoolean()) return "checkbox";
        if (value.isArray()) {
            if (!value.isEmpty() && value.get(0).has("name")) return "person";
            return "text";
        }
        String s = value.asText("");
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "date";
        if (s.startsWith("http")) return "url";
        if (s.endsWith("%") || s.matches("^\\d+(\\.\\d+)?%$")) return "progress";
        return "text";
    }
}
