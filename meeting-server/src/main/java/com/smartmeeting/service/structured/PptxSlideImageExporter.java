package com.smartmeeting.service.structured;

import com.smartmeeting.api.dto.structured.SlideDeckDto;
import com.smartmeeting.api.dto.structured.SlideDto;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PptxSlideImageExporter {

    private PptxSlideImageExporter() {
    }

    public static SlideDeckDto export(Path pptxPath, Path outputDir) throws IOException {
        if (pptxPath == null || !Files.isRegularFile(pptxPath)) {
            return SlideDeckDto.builder().totalSlides(0).slides(List.of()).build();
        }
        AgendaMaterialGeneratedImages.ensureDir(outputDir);
        try (InputStream in = Files.newInputStream(pptxPath);
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            Dimension pgSize = ppt.getPageSize();
            int slideCount = ppt.getSlides().size();
            if (AgendaMaterialGeneratedImages.isRasterCacheValid(
                    pptxPath, outputDir, slideCount, i -> AgendaMaterialGeneratedImages.slideImage(outputDir, i))) {
                return loadFromCache(outputDir, slideCount, pgSize);
            }
            List<SlideDto> slides = new ArrayList<>();
            int idx = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                BufferedImage img = new BufferedImage(pgSize.width, pgSize.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setPaint(Color.WHITE);
                g.fill(new Rectangle(pgSize.width, pgSize.height));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                slide.draw(g);
                g.dispose();
                ImageIO.write(img, "PNG", AgendaMaterialGeneratedImages.slideImage(outputDir, idx).toFile());
                String notes = "";
                try {
                    if (slide.getNotes() != null) {
                        notes = extractNotesText(slide.getNotes());
                    }
                } catch (Exception ignored) {
                }
                slides.add(SlideDto.builder()
                        .index(idx)
                        .width(pgSize.width)
                        .height(pgSize.height)
                        .notes(notes)
                        .build());
                idx++;
            }
            AgendaMaterialGeneratedImages.writeRasterCacheStamp(pptxPath, outputDir, slideCount);
            return SlideDeckDto.builder().totalSlides(slides.size()).slides(slides).build();
        } catch (Exception e) {
            return SlideDeckDto.builder().totalSlides(0).slides(List.of()).build();
        }
    }

    private static SlideDeckDto loadFromCache(Path outputDir, int slideCount, Dimension pgSize) {
        List<SlideDto> slides = new ArrayList<>();
        for (int i = 0; i < slideCount; i++) {
            slides.add(SlideDto.builder()
                    .index(i)
                    .width(pgSize.width)
                    .height(pgSize.height)
                    .notes("")
                    .build());
        }
        return SlideDeckDto.builder().totalSlides(slideCount).slides(slides).build();
    }

    private static String extractNotesText(org.apache.poi.xslf.usermodel.XSLFNotes notes) {
        if (notes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var shape : notes.getShapes()) {
            if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape ts) {
                String txt = ts.getText();
                if (txt != null && !txt.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(txt.trim());
                }
            }
        }
        return sb.toString();
    }
}
