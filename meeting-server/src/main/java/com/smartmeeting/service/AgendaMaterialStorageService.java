package com.smartmeeting.service;

import com.smartmeeting.api.dto.AgendaMaterialPreviewDto;
import com.smartmeeting.config.AgendaMaterialProperties;
import com.smartmeeting.config.agenda.AgendaMaterialDiskStorage;
import com.smartmeeting.config.agenda.AgendaMaterialDocxPreview;
import com.smartmeeting.config.agenda.AgendaMaterialFileSupport;
import com.smartmeeting.config.agenda.AgendaMaterialPathResolver;
import com.smartmeeting.api.dto.structured.*;
import com.smartmeeting.service.structured.AgendaMaterialGeneratedImages;
import com.smartmeeting.service.structured.ExcelStructuredExporter;
import com.smartmeeting.service.structured.PptxSlideImageExporter;
import com.smartmeeting.service.structured.CsvStructuredExporter;
import com.smartmeeting.service.structured.LocalDocStructuredExporter;
import com.smartmeeting.service.structured.LocalDocxStructuredExporter;
import com.smartmeeting.service.structured.PdfPageImageExporter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会序本地上传资料读取（运行时主持页下载/预览）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaMaterialStorageService {

    private final AgendaMaterialProperties properties;
    private final List<AgendaMaterialDiskStorage> readDisks = new ArrayList<>();

    @PostConstruct
    void init() {
        for (Path dir : AgendaMaterialPathResolver.candidateStorageDirs(properties.getStorageDir())) {
            readDisks.add(new AgendaMaterialDiskStorage(
                    dir, properties.getMaxImageBytes(), properties.getMaxDocBytes()));
        }
        log.info("Agenda material read paths: {}", readDisks.stream()
                .map(d -> d.storageDir().toAbsolutePath().toString())
                .distinct()
                .toList());
    }

    public Optional<AgendaMaterialDiskStorage.StoredMaterial> loadMeta(String fileId) throws IOException {
        for (AgendaMaterialDiskStorage disk : readDisks) {
            Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = disk.loadMeta(fileId);
            if (meta.isPresent()) {
                return meta;
            }
        }
        return Optional.empty();
    }

    public Optional<Path> resolvePath(String fileId) throws IOException {
        for (AgendaMaterialDiskStorage disk : readDisks) {
            Optional<Path> path = disk.resolveDataPath(fileId);
            if (path.isPresent()) {
                return path;
            }
        }
        return Optional.empty();
    }

    public Optional<AgendaMaterialPreviewDto> previewMaterial(String fileId) throws IOException {
        Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = loadMeta(fileId);
        if (meta.isEmpty()) {
            return Optional.empty();
        }
        Optional<Path> path = resolvePath(fileId);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        String mime = meta.get().mimeType();
        String pathStr = path.get().toString().toLowerCase();
        Path dataPath = path.get();
        Path generatedDir = AgendaMaterialGeneratedImages.dirForDataFile(dataPath, meta.get().fileId());
        if (pathStr.endsWith(".pptx") || pathStr.endsWith(".ppt")) {
            SlideDeckDto deck = PptxSlideImageExporter.export(dataPath, generatedDir);
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .structuredContent(deck)
                    .contentType("ppt_slides")
                    .plainText(deck.getTotalSlides() + " 页幻灯片")
                    .html("")
                    .build());
        }
        if (pathStr.endsWith(".xlsx") || pathStr.endsWith(".xls")) {
            ExcelWorkbookDto wb = ExcelStructuredExporter.export(dataPath);
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .structuredContent(wb)
                    .contentType("excel_workbook")
                    .plainText(wb.getSheets().size() + " 个工作表")
                    .html("")
                    .build());
        }
        if (pathStr.endsWith(".pdf")) {
            PdfDocumentDto pdf = PdfPageImageExporter.export(dataPath, generatedDir);
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .structuredContent(pdf)
                    .contentType("pdf_pages")
                    .plainText(pdf.getTotalPages() + " 页")
                    .html("")
                    .build());
        }
        if (pathStr.endsWith(".csv")) {
            SheetStructuredDto sheet = CsvStructuredExporter.export(path.get());
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .contentType("sheet_cells")
                    .structuredContent(sheet)
                    .plainText(sheet.getRows().size() + " 行")
                    .html("")
                    .build());
        }
        if (AgendaMaterialFileSupport.isDocMime(mime) || pathStr.endsWith(".docx") || pathStr.endsWith(".doc")) {
            if (pathStr.endsWith(".docx")) {
                try {
                    var blocks = LocalDocxStructuredExporter.export(path.get());
                    if (!blocks.isEmpty()) {
                        return Optional.of(AgendaMaterialPreviewDto.builder()
                                .contentType("docx_blocks")
                                .structuredContent(blocks)
                                .plainText(blocks.stream()
                                        .map(b -> b.getText() != null ? b.getText() : "")
                                        .filter(t -> !t.isBlank())
                                        .reduce((a, b) -> a + "\n" + b)
                                        .orElse(""))
                                .html("")
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("local docx structured export failed fileId={}: {}", meta.get().fileId(), e.getMessage());
                }
            }
            if (pathStr.endsWith(".doc")) {
                try {
                    var blocks = LocalDocStructuredExporter.export(path.get());
                    if (!blocks.isEmpty()) {
                        return Optional.of(AgendaMaterialPreviewDto.builder()
                                .contentType("docx_blocks")
                                .structuredContent(blocks)
                                .plainText(blocks.stream()
                                        .map(b -> b.getText() != null ? b.getText() : "")
                                        .filter(t -> !t.isBlank())
                                        .reduce((a, b) -> a + "\n" + b)
                                        .orElse(""))
                                .html("")
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("local doc structured export failed fileId={}: {}", meta.get().fileId(), e.getMessage());
                }
            }
            AgendaMaterialDocxPreview.DocxPreview preview = AgendaMaterialDocxPreview.extractPreview(path.get());
            if (preview.html() == null || preview.html().isBlank()) {
                if (preview.plainText() == null || preview.plainText().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(AgendaMaterialPreviewDto.builder()
                        .plainText(preview.plainText())
                        .html("")
                        .build());
            }
            return Optional.of(AgendaMaterialPreviewDto.builder()
                    .html(preview.html())
                    .plainText(preview.plainText())
                    .build());
        }
        return Optional.empty();
    }

    /**
     * 获取本地资料的结构化内容（供 agenda-doc-content 接口使用）。
     * 返回 contentType + structuredContent 的预览，或空 Optional。
     */
    public Optional<StructuredMaterialPreview> structuredPreview(String fileId) throws IOException {
        Optional<AgendaMaterialDiskStorage.StoredMaterial> meta = loadMeta(fileId);
        if (meta.isEmpty()) return Optional.empty();
        Optional<Path> path = resolvePath(fileId);
        if (path.isEmpty()) return Optional.empty();
        String pathStr = path.get().toString().toLowerCase();
        Path dataPath = path.get();
        Path generatedDir = AgendaMaterialGeneratedImages.dirForDataFile(dataPath, fileId);
        try {
            if (pathStr.endsWith(".pptx") || pathStr.endsWith(".ppt")) {
                SlideDeckDto deck = PptxSlideImageExporter.export(dataPath, generatedDir);
                return Optional.of(new StructuredMaterialPreview("ppt_slides", deck));
            }
            if (pathStr.endsWith(".xlsx") || pathStr.endsWith(".xls")) {
                ExcelWorkbookDto wb = ExcelStructuredExporter.export(dataPath);
                return Optional.of(new StructuredMaterialPreview("excel_workbook", wb));
            }
            if (pathStr.endsWith(".pdf")) {
                PdfDocumentDto pdf = PdfPageImageExporter.export(dataPath, generatedDir);
                return Optional.of(new StructuredMaterialPreview("pdf_pages", pdf));
            }
            if (pathStr.endsWith(".docx")) {
                var blocks = LocalDocxStructuredExporter.export(dataPath);
                if (!blocks.isEmpty()) {
                    return Optional.of(new StructuredMaterialPreview("docx_blocks", blocks));
                }
            }
            if (pathStr.endsWith(".doc")) {
                var blocks = LocalDocStructuredExporter.export(dataPath);
                if (!blocks.isEmpty()) {
                    return Optional.of(new StructuredMaterialPreview("docx_blocks", blocks));
                }
            }
            if (pathStr.endsWith(".csv")) {
                return Optional.of(new StructuredMaterialPreview("sheet_cells", CsvStructuredExporter.export(dataPath)));
            }
        } catch (Exception e) {
            log.warn("structuredPreview failed fileId={}: {}", fileId, e.getMessage());
        }
        return Optional.empty();
    }

    public record StructuredMaterialPreview(String contentType, Object structuredContent) {}

    /** @deprecated 使用 {@link #previewMaterial(String)} */
    public Optional<String> previewPlainText(String fileId) throws IOException {
        return previewMaterial(fileId).map(d -> {
            if (d.getHtml() != null && !d.getHtml().isBlank()) {
                return d.getPlainText() != null ? d.getPlainText() : "";
            }
            return d.getPlainText();
        });
    }

    public boolean isInlineDisposition(String mimeType) {
        return AgendaMaterialFileSupport.isImageMime(mimeType);
    }

    /**
     * 返回本地资料生成的图片路径（PPT/PDF 页面图片）。
     */
    public java.nio.file.Path resolveGeneratedImagePath(String fileId, int page) throws java.io.IOException {
        Optional<java.nio.file.Path> pathOpt = resolvePath(fileId);
        if (pathOpt.isEmpty()) {
            return null;
        }
        java.nio.file.Path generatedDir = AgendaMaterialGeneratedImages.dirForDataFile(pathOpt.get(), fileId);
        java.nio.file.Path imagePath = AgendaMaterialGeneratedImages.pageImage(generatedDir, page);
        if (java.nio.file.Files.exists(imagePath)) {
            return imagePath;
        }
        imagePath = AgendaMaterialGeneratedImages.slideImage(generatedDir, page);
        if (java.nio.file.Files.exists(imagePath)) {
            return imagePath;
        }
        return null;
    }

}
