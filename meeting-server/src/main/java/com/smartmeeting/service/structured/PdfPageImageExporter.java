package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.PdfDocumentDto;
import com.smartmeeting.api.dto.structured.PdfPageDto;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfPageImageExporter {

    private static final float DPI = 150f;

    private PdfPageImageExporter() {
    }

    public static PdfDocumentDto export(Path pdfPath, Path outputDir) throws IOException {
        if (pdfPath == null || !Files.isRegularFile(pdfPath)) {
            return PdfDocumentDto.builder().totalPages(0).pages(List.of()).build();
        }
        AgendaMaterialGeneratedImages.ensureDir(outputDir);
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            int pageCount = doc.getNumberOfPages();
            if (AgendaMaterialGeneratedImages.isRasterCacheValid(
                    pdfPath, outputDir, pageCount, i -> AgendaMaterialGeneratedImages.pageImage(outputDir, i))) {
                return loadFromCache(outputDir, pageCount);
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            List<PdfPageDto> pages = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, DPI);
                ImageIO.write(img, "PNG", AgendaMaterialGeneratedImages.pageImage(outputDir, i).toFile());
                pages.add(PdfPageDto.builder()
                        .index(i)
                        .width(img.getWidth())
                        .height(img.getHeight())
                        .build());
            }
            AgendaMaterialGeneratedImages.writeRasterCacheStamp(pdfPath, outputDir, pageCount);
            return PdfDocumentDto.builder().totalPages(pageCount).pages(pages).build();
        }
    }

    private static PdfDocumentDto loadFromCache(Path outputDir, int pageCount) throws IOException {
        List<PdfPageDto> pages = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            Path imagePath = AgendaMaterialGeneratedImages.pageImage(outputDir, i);
            BufferedImage img = ImageIO.read(imagePath.toFile());
            int w = img != null ? img.getWidth() : 0;
            int h = img != null ? img.getHeight() : 0;
            pages.add(PdfPageDto.builder().index(i).width(w).height(h).build());
        }
        return PdfDocumentDto.builder().totalPages(pageCount).pages(pages).build();
    }
}
