package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.SheetStructuredDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 本地 CSV → {@link SheetStructuredDto}（复用 sheet 表格渲染）。 */
public final class CsvStructuredExporter {

    private CsvStructuredExporter() {
    }

    public static SheetStructuredDto export(Path csvPath) throws IOException {
        return export(csvPath, StandardCharsets.UTF_8);
    }

    public static SheetStructuredDto export(Path csvPath, Charset charset) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        if (rows.isEmpty()) {
            return SheetStructuredDto.builder()
                    .sheetName("CSV")
                    .headers(List.of())
                    .rows(List.of())
                    .mergedRanges(List.of())
                    .columnWidths(List.of())
                    .headerRowCount(1)
                    .build();
        }
        List<String> headers = rows.get(0);
        List<List<String>> data = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();
        return SheetStructuredDto.builder()
                .sheetName(csvPath.getFileName() != null ? csvPath.getFileName().toString() : "CSV")
                .headers(headers)
                .rows(data)
                .mergedRanges(List.of())
                .columnWidths(List.of())
                .headerRowCount(1)
                .build();
    }

    static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        if (line == null) {
            return cells;
        }
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }
}
