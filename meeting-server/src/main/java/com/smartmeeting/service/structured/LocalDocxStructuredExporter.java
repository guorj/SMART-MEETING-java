package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.DocxBlockDto;
import com.smartmeeting.api.dto.structured.RichTextRunDto;
import org.apache.poi.xwpf.usermodel.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地 .docx（OOXML）→ {@link DocxBlockDto} 列表，供主持页与飞书 docx 共用渲染器。
 */
public final class LocalDocxStructuredExporter {

    private LocalDocxStructuredExporter() {
    }

    public static List<DocxBlockDto> export(Path docxPath) throws IOException {
        if (docxPath == null || !Files.isRegularFile(docxPath)) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(docxPath);
             XWPFDocument doc = new XWPFDocument(in)) {
            List<DocxBlockDto> blocks = new ArrayList<>();
            for (IBodyElement element : doc.getBodyElements()) {
                DocxBlockDto block = exportBodyElement(element);
                if (block != null) {
                    blocks.add(block);
                }
            }
            return blocks;
        }
    }

    private static DocxBlockDto exportBodyElement(IBodyElement element) {
        if (element instanceof XWPFParagraph para) {
            return exportParagraph(para);
        }
        if (element instanceof XWPFTable table) {
            return exportTable(table);
        }
        return null;
    }

    private static DocxBlockDto exportParagraph(XWPFParagraph para) {
        String type = headingType(para);
        List<RichTextRunDto> runs = extractRuns(para);
        String text = runs.stream().map(RichTextRunDto::getText).reduce("", String::concat).trim();
        if (text.isEmpty() && runs.isEmpty()) {
            return null;
        }
        return DocxBlockDto.builder().type(type).text(text).runs(runs).build();
    }

    private static String headingType(XWPFParagraph para) {
        String style = para.getStyle();
        if (style != null) {
            String lower = style.toLowerCase();
            if (lower.contains("heading1") || "1".equals(lower) || lower.endsWith("heading 1")) {
                return "heading1";
            }
            if (lower.contains("heading2") || "2".equals(lower) || lower.endsWith("heading 2")) {
                return "heading2";
            }
            if (lower.contains("heading3") || "3".equals(lower) || lower.endsWith("heading 3")) {
                return "heading3";
            }
            if (lower.contains("heading4") || "4".equals(lower)) {
                return "heading4";
            }
            if (lower.contains("heading5") || "5".equals(lower)) {
                return "heading5";
            }
            if (lower.contains("heading6") || "6".equals(lower)) {
                return "heading6";
            }
        }
        return "paragraph";
    }

    private static List<RichTextRunDto> extractRuns(XWPFParagraph para) {
        List<RichTextRunDto> runs = new ArrayList<>();
        for (XWPFRun run : para.getRuns()) {
            String content = run.text();
            if (content == null || content.isEmpty()) {
                continue;
            }
            runs.add(RichTextRunDto.builder()
                    .text(content)
                    .bold(run.isBold() ? true : null)
                    .italic(run.isItalic() ? true : null)
                    .underline(run.getUnderline() != UnderlinePatterns.NONE ? true : null)
                    .strikethrough(run.isStrikeThrough() ? true : null)
                    .build());
        }
        return runs;
    }

    private static DocxBlockDto exportTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().trim());
            }
            sb.append(String.join(" | ", cells)).append('\n');
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return DocxBlockDto.builder()
                .type("paragraph")
                .text(text)
                .runs(List.of(RichTextRunDto.builder().text(text).build()))
                .build();
    }
}
