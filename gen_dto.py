import os, sys
base = r'D:\openclaw\workspace-clone\projects\smart-meeting-java\meeting-server\src\main\java\com\smartmeeting'
dto_dir = os.path.join(base, 'api', 'dto', 'structured')
exp_dir = os.path.join(base, 'service', 'structured')
os.makedirs(exp_dir, exist_ok=True)

files = {}

# DTOs
files[dto_dir + r'\PdfPageDto.java'] = r'''package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PdfPageDto { private int index; private String imageUrl; private int width; private int height; }
'''

files[dto_dir + r'\PdfDocumentDto.java'] = r'''package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PdfDocumentDto { private int totalPages; private List<PdfPageDto> pages; }
'''

files[dto_dir + r'\MergedRangeDto.java'] = r'''package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MergedRangeDto { private int startRow; private int endRow; private int startCol; private int endCol; }
'''

files[dto_dir + r'\ExcelSheetDto.java'] = r'''package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelSheetDto {
    private String sheetName; private int sheetIndex; private List<String> headers;
    private List<List<String>> rows; private List<MergedRangeDto> mergedRanges;
    private List<Integer> columnWidths; private int headerRowCount;
}
'''

files[dto_dir + r'\ExcelWorkbookDto.java'] = r'''package com.smartmeeting.api.dto.structured;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExcelWorkbookDto { private List<ExcelSheetDto> sheets; }
'''

# Exporters
files[exp_dir + r'\DocxBlockStructuredExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.DocxBlockDto;
import com.smartmeeting.api.dto.structured.RichTextRunDto;
import java.util.*;

public final class DocxBlockStructuredExporter {
    private static final Map<Integer, String> BLOCK_TYPE_FIELD = Map.ofEntries(
        Map.entry(2,"text"), Map.entry(3,"heading1"), Map.entry(4,"heading2"), Map.entry(5,"heading3"),
        Map.entry(6,"heading4"), Map.entry(7,"heading5"), Map.entry(8,"heading6"),
        Map.entry(9,"heading7"), Map.entry(10,"heading8"), Map.entry(11,"heading9"),
        Map.entry(12,"bullet"), Map.entry(13,"ordered"), Map.entry(15,"quote"), Map.entry(17,"todo"));

    private DocxBlockStructuredExporter() {}

    public static List<DocxBlockDto> exportBlocks(JsonNode items) {
        List<DocxBlockDto> blocks = new ArrayList<>();
        if (items == null || !items.isArray()) return blocks;
        for (JsonNode item : items) {
            blocks.add(exportBlock(item));
        }
        return blocks;
    }

    public static DocxBlockDto exportBlock(JsonNode block) {
        if (block == null || block.isNull()) return null;
        int blockType = block.path("block_type").asInt(0);
        if (blockType == 22) return DocxBlockDto.builder().type("divider").build();
        if (blockType == 23) {
            String key = block.path("image").path("token").asText("");
            return DocxBlockDto.builder().type("image").imageKey(key.isEmpty() ? null : key).build();
        }
        String field = BLOCK_TYPE_FIELD.getOrDefault(blockType, "text");
        JsonNode node = block.get(field);
        if (node == null || !node.isObject()) node = findFirstElementsObject(block);
        String text = "";
        List<RichTextRunDto> runs = Collections.emptyList();
        if (node != null && node.has("elements")) {
            var result = extractRuns(node.get("elements"));
            text = result.text;
            runs = result.runs;
        }
        String type = switch (blockType) {
            case 3 -> "heading1"; case 4 -> "heading2"; case 5 -> "heading3";
            case 6 -> "heading4"; case 7,8,9,10,11 -> "heading5";
            case 12 -> "bullet"; case 13 -> "ordered"; case 15 -> "quote";
            case 17 -> "todo"; default -> "paragraph";
        };
        boolean checked = blockType == 17 && node != null && node.path("style").asText("").contains("done");
        return DocxBlockDto.builder().type(type).text(text).runs(runs).checked(checked).build();
    }

    private static JsonNode findFirstElementsObject(JsonNode block) {
        var it = block.fields();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            if (Set.of("block_id","block_type","parent_id","children","comment_ids").contains(key)) continue;
            JsonNode val = e.getValue();
            if (val != null && val.isObject() && val.has("elements") && val.get("elements").isArray()) return val;
        }
        return null;
    }

    private static record RunResult(String text, List<RichTextRunDto> runs) {}
    private static RunResult extractRuns(JsonNode elements) {
        if (elements == null || !elements.isArray()) return new RunResult("", Collections.emptyList());
        StringBuilder sb = new StringBuilder();
        List<RichTextRunDto> runs = new ArrayList<>();
        for (JsonNode el : elements) {
            if (el == null || !el.isObject()) continue;
            if (el.has("text_run")) {
                JsonNode tr = el.path("text_run");
                String content = tr.path("content").asText("");
                sb.append(content);
                JsonNode style = tr.path("text_element_style");
                runs.add(RichTextRunDto.builder()
                    .text(content)
                    .bold(boolAttr(style, "bold"))
                    .italic(boolAttr(style, "italic"))
                    .strikethrough(boolAttr(style, "strikethrough"))
                    .underline(boolAttr(style, "underline"))
                    .link(style.path("link").path("url").asText(null))
                    .build());
            }
        }
        return new RunResult(sb.toString().trim(), runs);
    }
    private static Boolean boolAttr(JsonNode style, String attr) {
        if (style == null || !style.isObject()) return null;
        JsonNode v = style.get(attr);
        return v != null && v.asBoolean() ? true : null;
    }
}
'''

files[exp_dir + r'\BitableStructuredExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.*;
import com.smartmeeting.matterprogress.feishu.BitableFieldFormatter;
import java.util.*;

public final class BitableStructuredExporter {
    private BitableStructuredExporter() {}

    public static BitableStructuredDto export(List<JsonNode> items, String tableName) {
        if (items == null || items.isEmpty()) return BitableStructuredDto.builder()
            .tableName(tableName).columns(List.of()).records(List.of()).build();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (JsonNode item : items) {
            JsonNode fields = item.path("fields");
            if (!fields.isObject()) continue;
            var it = fields.fields();
            while (it.hasNext()) {
                var e = it.next();
                fieldTypes.putIfAbsent(e.getKey(), guessFieldType(e.getValue()));
            }
        }
        List<BitableColumnDto> columns = new ArrayList<>();
        for (var e : fieldTypes.entrySet()) {
            columns.add(BitableColumnDto.builder().name(e.getKey()).type(e.getValue()).build());
        }
        List<BitableRecordDto> records = new ArrayList<>();
        for (JsonNode item : items) {
            Map<String, String> fieldValues = new LinkedHashMap<>();
            JsonNode fields = item.path("fields");
            if (fields.isObject()) {
                var it = fields.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    fieldValues.put(e.getKey(), BitableFieldFormatter.format(e.getValue(), e.getKey()));
                }
            }
            records.add(BitableRecordDto.builder().fields(fieldValues).build());
        }
        return BitableStructuredDto.builder().tableName(tableName).columns(columns).records(records).build();
    }

    public static BitableStructuredDto exportMultiTable(List<BitablePlainTextExporter.BitableTableSlice> tables) {
        if (tables == null || tables.isEmpty()) return BitableStructuredDto.builder()
            .tableName("").columns(List.of()).records(List.of()).build();
        List<BitableColumnDto> columns = new ArrayList<>();
        List<BitableRecordDto> records = new ArrayList<>();
        String firstTableName = "";
        for (var slice : tables) {
            if (firstTableName.isEmpty()) firstTableName = slice.tableName();
            List<JsonNode> items = slice.items() != null ? slice.items() : List.of();
            var dto = export(items, slice.tableName());
            columns.addAll(dto.getColumns());
            records.addAll(dto.getRecords());
        }
        return BitableStructuredDto.builder().tableName(firstTableName).columns(columns).records(records).build();
    }

    private static String guessFieldType(JsonNode value) {
        if (value == null || value.isNull()) return "text";
        if (value.isNumber()) return "number";
        if (value.isArray()) {
            if (!value.isEmpty() && value.get(0).has("name")) return "person";
            return "text";
        }
        String s = value.asText("");
        if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) return "date";
        if (s.startsWith("http")) return "url";
        return "text";
    }
}
'''

files[exp_dir + r'\SheetStructuredExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.structured.*;
import java.util.*;

public final class SheetStructuredExporter {
    private SheetStructuredExporter() {}

    public static SheetStructuredDto export(String sheetName, JsonNode valuesNode) {
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
        List<String> headers = rows.isEmpty() ? List.of() : rows.get(0);
        List<List<String>> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();
        return SheetStructuredDto.builder()
            .sheetName(sheetName).columns(List.of()).headers(headers)
            .rows(dataRows).mergedRanges(List.of()).columnWidths(List.of()).headerRowCount(1).build();
    }
}
'''

files[exp_dir + r'\ExcelStructuredExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.smartmeeting.api.dto.structured.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
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
            merged.add(MergedRangeDto.builder()
                .startRow(range.getFirstRow()).endRow(range.getLastRow())
                .startCol(range.getFirstColumn()).endCol(range.getLastColumn()).build());
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
'''

files[exp_dir + r'\PdfPageImageExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.smartmeeting.api.dto.structured.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class PdfPageImageExporter {
    private static final float DPI = 150f;
    private PdfPageImageExporter() {}

    public static PdfDocumentDto export(Path pdfPath, String meetingId, String fileId) throws IOException {
        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return PdfDocumentDto.builder().totalPages(0).pages(java.util.List.of()).build();
        }
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            List<PdfPageDto> pages = new ArrayList<>();
            String imagesDir = resolveImagesDir(meetingId, fileId);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, DPI);
                String fileName = "page_" + i + ".png";
                Path outPath = Paths.get(imagesDir);
                Files.createDirectories(outPath);
                ImageIO.write(img, "PNG", outPath.resolve(fileName).toFile());
                pages.add(PdfPageDto.builder().index(i)
                    .imageUrl("/api/v1/meetings/" + meetingId + "/agenda-materials/proxy-image?fileId="
                        + fileId + "&page=" + i)
                    .width(img.getWidth()).height(img.getHeight()).build());
            }
            return PdfDocumentDto.builder().totalPages(doc.getNumberOfPages()).pages(pages).build();
        }
    }

    public static Path resolvePageImage(Path storageDir, String fileId, int pageIndex) {
        return storageDir.resolve(fileId).resolve("pdf_images").resolve("page_" + pageIndex + ".png");
    }

    private static String resolveImagesDir(String meetingId, String fileId) {
        String base = System.getProperty("agenda.materials.storage-dir", "data/agenda-materials");
        return base + "/" + fileId + "/pdf_images";
    }
}
'''

files[exp_dir + r'\PptxSlideImageExporter.java'] = r'''package com.smartmeeting.service.structured;
import com.smartmeeting.api.dto.structured.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class PptxSlideImageExporter {
    private static final int SLIDE_WIDTH = 1920;
    private static final int SLIDE_HEIGHT = 1080;
    private PptxSlideImageExporter() {}

    public static SlideDeckDto export(Path pptxPath, String meetingId, String fileId) throws IOException {
        if (pptxPath == null || !Files.isRegularFile(pptxPath)) {
            return SlideDeckDto.builder().totalSlides(0).slides(List.of()).build();
        }
        try (InputStream in = Files.newInputStream(pptxPath);
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            Dimension pgSize = ppt.getPageSize();
            List<SlideDto> slides = new ArrayList<>();
            String imagesDir = resolveImagesDir(fileId);
            Path outPath = Paths.get(imagesDir);
            Files.createDirectories(outPath);
            int idx = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                BufferedImage img = new BufferedImage(pgSize.width, pgSize.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setPaint(Color.WHITE);
                g.fill(new Rectangle(pgSize.width, pgSize.height));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                slide.draw(g);
                g.dispose();
                String fileName = "slide_" + idx + ".png";
                ImageIO.write(img, "PNG", outPath.resolve(fileName).toFile());
                String notes = "";
                try { if (slide.getNotes() != null) notes = slide.getNotes().getEditText().getText(); } catch (Exception ignored) {}
                slides.add(SlideDto.builder().index(idx)
                    .imageUrl("/api/v1/meetings/" + meetingId + "/agenda-materials/proxy-image?fileId="
                        + fileId + "&slide=" + idx)
                    .width(pgSize.width).height(pgSize.height).notes(notes).build());
                idx++;
            }
            return SlideDeckDto.builder().totalSlides(slides.size()).slides(slides).build();
        } catch (Exception e) {
            return SlideDeckDto.builder().totalSlides(0).slides(List.of())
                .fallbackText("PPT 渲染失败: " + e.getMessage()).build();
        }
    }

    public static Path resolveSlideImage(Path storageDir, String fileId, int slideIndex) {
        return storageDir.resolve(fileId).resolve("ppt_images").resolve("slide_" + slideIndex + ".png");
    }

    private static String resolveImagesDir(String fileId) {
        String base = System.getProperty("agenda.materials.storage-dir", "data/agenda-materials");
        return base + "/" + fileId + "/ppt_images";
    }
}
'''

# Write import for ImageIO in PptxSlideImageExporter
import re
for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content.lstrip('\n'))
    print(f'Created: {os.path.basename(path)}')
print('Done')
