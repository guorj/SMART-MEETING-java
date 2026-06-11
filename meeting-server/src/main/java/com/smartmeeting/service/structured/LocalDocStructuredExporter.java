package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.DocxBlockDto;
import com.smartmeeting.api.dto.structured.RichTextRunDto;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 本地旧版 .doc（HWPF）→ {@link DocxBlockDto} 列表。 */
public final class LocalDocStructuredExporter {

    private LocalDocStructuredExporter() {
    }

    public static List<DocxBlockDto> export(Path docPath) throws IOException {
        if (docPath == null || !Files.isRegularFile(docPath)) {
            return List.of();
        }
        try (InputStream in = Files.newInputStream(docPath);
             HWPFDocument doc = new HWPFDocument(in)) {
            Range range = doc.getRange();
            List<DocxBlockDto> blocks = new ArrayList<>();
            for (int i = 0; i < range.numParagraphs(); i++) {
                Paragraph para = range.getParagraph(i);
                String text = para.text();
                if (text == null) {
                    continue;
                }
                text = text.replace('\u0007', ' ').trim();
                if (text.isEmpty()) {
                    continue;
                }
                blocks.add(DocxBlockDto.builder()
                        .type("paragraph")
                        .text(text)
                        .runs(List.of(RichTextRunDto.builder().text(text).build()))
                        .build());
            }
            return blocks;
        }
    }
}
