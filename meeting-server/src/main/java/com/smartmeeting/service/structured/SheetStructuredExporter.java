package com.smartmeeting.service.structured;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.*;
import com.smartmeeting.matterprogress.feishu.FeishuSpreadsheetPlainTextFetcher;
import java.util.*;

public final class SheetStructuredExporter {
    private SheetStructuredExporter() {}

    public static SheetStructuredDto export(String sheetName, JsonNode valuesNode) {
        return export(sheetName, valuesNode, List.of());
    }

    public static SheetStructuredDto export(
            String sheetName,
            JsonNode valuesNode,
            List<FeishuSpreadsheetPlainTextFetcher.MergeRange> merges) {
        List<List<String>> rows = new ArrayList<>();
        int maxCols = 0;
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode rowNode : valuesNode) {
                List<String> row = new ArrayList<>();
                if (rowNode != null && rowNode.isArray()) {
                    for (JsonNode cell : rowNode) {
                        row.add(cell == null || cell.isNull() ? "" : cell.asText("").trim());
                    }
                }
                if (!row.isEmpty()) maxCols = Math.max(maxCols, row.size());
                rows.add(row);
            }
        }
        for (List<String> row : rows) { while (row.size() < maxCols) row.add(""); }
        while (!rows.isEmpty() && isEmptyRow(rows.get(rows.size() - 1))) {
            rows.remove(rows.size() - 1);
        }
        rows = trimTrailingEmptyColumns(rows);
        List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
        List<List<String>> dataRows = new ArrayList<>();
        if (rows.size() > 1) {
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (!isEmptyRow(row)) {
                    dataRows.add(row);
                }
            }
        }
        List<MergedRangeDto> mergedRanges = toMergedDtos(merges);
        return SheetStructuredDto.builder()
            .sheetName(sheetName).headers(headers)
            .rows(dataRows).mergedRanges(mergedRanges).columnWidths(List.of()).headerRowCount(1).build();
    }

    static List<List<String>> trimTrailingEmptyColumns(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? List.of() : rows;
        }
        int lastCol = -1;
        for (List<String> row : rows) {
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                if (cell != null && !cell.isBlank()) {
                    lastCol = Math.max(lastCol, c);
                }
            }
        }
        if (lastCol < 0) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> trimmed = new ArrayList<>();
            for (int c = 0; c <= lastCol; c++) {
                trimmed.add(row != null && c < row.size() ? row.get(c) : "");
            }
            out.add(trimmed);
        }
        return out;
    }

    static boolean isEmptyRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    static List<MergedRangeDto> toMergedDtos(List<FeishuSpreadsheetPlainTextFetcher.MergeRange> merges) {
        if (merges == null || merges.isEmpty()) {
            return List.of();
        }
        List<MergedRangeDto> out = new ArrayList<>();
        for (FeishuSpreadsheetPlainTextFetcher.MergeRange m : merges) {
            out.add(MergedRangeDto.builder()
                    .startRow(m.startRow())
                    .endRow(m.endRow())
                    .startCol(m.startCol())
                    .endCol(m.endCol())
                    .build());
        }
        return out;
    }
}
