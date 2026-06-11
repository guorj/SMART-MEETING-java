package com.smartmeeting.service.structured;
import com.smartmeeting.api.dto.structured.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

public final class ExcelStructuredExporter {
    private ExcelStructuredExporter() {}

    public static ExcelWorkbookDto export(Path filePath) throws IOException {
        try (InputStream in = filePath.toFile().exists() ? new FileInputStream(filePath.toFile()) :
                filePath.toAbsolutePath().toFile().exists() ? new FileInputStream(filePath.toAbsolutePath().toFile()) : null) {
            if (in == null) return ExcelWorkbookDto.builder().sheets(List.of()).build();
            Workbook wb = WorkbookFactory.create(in);
            List<ExcelSheetDto> sheets = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                sheets.add(exportSheet(sheet, i));
            }
            wb.close();
            return ExcelWorkbookDto.builder().sheets(sheets).build();
        }
    }

    private static ExcelSheetDto exportSheet(Sheet sheet, int sheetIndex) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        List<MergedRangeDto> merged = new ArrayList<>();
        List<Integer> colWidths = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        int maxCols = 0;
        if (headerRow != null) {
            maxCols = headerRow.getLastCellNum();
            for (int c = 0; c < maxCols; c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cellToString(cell));
                colWidths.add(sheet.getColumnWidth(c) / 256);
            }
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            List<String> rowData = new ArrayList<>();
            if (row != null) {
                for (int c = 0; c < maxCols; c++) {
                    Cell cell = row.getCell(c);
                    rowData.add(cellToString(cell));
                }
            } else {
                for (int c = 0; c < maxCols; c++) rowData.add("");
            }
            rows.add(rowData);
        }
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress range = sheet.getMergedRegion(i);
            if (range.getLastRow() < 1) {
                continue;
            }
            merged.add(MergedRangeDto.builder()
                .startRow(Math.max(0, range.getFirstRow() - 1))
                .endRow(range.getLastRow() - 1)
                .startCol(range.getFirstColumn())
                .endCol(range.getLastColumn())
                .build());
        }
        return ExcelSheetDto.builder().sheetName(sheet.getSheetName()).sheetIndex(sheetIndex)
            .headers(headers).rows(rows).mergedRanges(merged).columnWidths(colWidths).headerRowCount(1).build();
    }

    private static String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue().toString() : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
