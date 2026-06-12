package com.smartmeeting.service.structured;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.common.usermodel.fonts.FontInfo;
import org.apache.poi.sl.draw.DrawFontManagerDefault;
import org.apache.poi.sl.draw.Drawable;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PPT 栅格化渲染：注册 CJK 字体、强制 TextRun 字体，并通过 {@link Drawable#FONT_HANDLER} 兜底。
 */
@Slf4j
public final class PptxSlideRenderSupport {

    /** 变更渲染逻辑时递增，使旧 PNG 缓存失效。 */
    public static final int CACHE_VERSION = 2;

    private static volatile Font cjkFont;
    private static volatile String cjkFontFamily;
    private static volatile DrawFontManagerDefault cjkFontManager;

    static {
        initFonts();
    }

    private PptxSlideRenderSupport() {
    }

    public static void applyRenderHints(Graphics2D g) {
        if (g == null) {
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        if (cjkFontManager != null) {
            g.setRenderingHint(Drawable.FONT_HANDLER, cjkFontManager);
        }
        if (cjkFontFamily == null || cjkFontFamily.isBlank()) {
            return;
        }
        Map<String, String> fallback = new HashMap<>();
        fallback.put("*", cjkFontFamily);
        fallback.put("Calibri", cjkFontFamily);
        fallback.put("Arial", cjkFontFamily);
        fallback.put("Times New Roman", cjkFontFamily);
        fallback.put("微软雅黑", cjkFontFamily);
        fallback.put("Microsoft YaHei", cjkFontFamily);
        fallback.put("宋体", cjkFontFamily);
        fallback.put("SimSun", cjkFontFamily);
        fallback.put("黑体", cjkFontFamily);
        fallback.put("SimHei", cjkFontFamily);
        fallback.put("等线", cjkFontFamily);
        fallback.put("DengXian", cjkFontFamily);
        fallback.put("PingFang SC", cjkFontFamily);
        fallback.put("Noto Sans CJK SC", cjkFontFamily);
        g.setRenderingHint(Drawable.FONT_FALLBACK, fallback);

        Map<String, String> fontMap = new HashMap<>();
        fontMap.put("微软雅黑", cjkFontFamily);
        fontMap.put("Microsoft YaHei", cjkFontFamily);
        fontMap.put("宋体", cjkFontFamily);
        fontMap.put("SimSun", cjkFontFamily);
        fontMap.put("黑体", cjkFontFamily);
        fontMap.put("SimHei", cjkFontFamily);
        fontMap.put("等线", cjkFontFamily);
        g.setRenderingHint(Drawable.FONT_MAP, fontMap);
        g.setRenderingHint(Drawable.DEFAULT_CHARSET, Charset.forName("GBK"));
    }

    /** 导出前将幻灯片内全部 TextRun 字体改为已注册的 CJK 字体（表格单元格必须显式处理）。 */
    public static void normalizeSlideFonts(XSLFSlide slide) {
        if (slide == null || cjkFontFamily == null || cjkFontFamily.isBlank()) {
            return;
        }
        for (XSLFShape shape : slide.getShapes()) {
            normalizeShapeFonts(shape);
        }
    }

    private static void normalizeShapeFonts(XSLFShape shape) {
        if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                for (XSLFTableCell cell : row.getCells()) {
                    normalizeTextShape(cell);
                }
            }
            return;
        }
        if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                normalizeShapeFonts(child);
            }
            return;
        }
        if (shape instanceof XSLFTextShape textShape) {
            normalizeTextShape(textShape);
        }
    }

    private static void normalizeTextShape(XSLFTextShape textShape) {
        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                run.setFontFamily(cjkFontFamily, FontGroup.LATIN);
                run.setFontFamily(cjkFontFamily, FontGroup.EAST_ASIAN);
                run.setFontFamily(cjkFontFamily, FontGroup.COMPLEX_SCRIPT);
            }
        }
    }

    private static void initFonts() {
        if (tryRegisterFromPath(Path.of("C:", "Windows", "Fonts", "msyh.ttc"))) {
            return;
        }
        if (tryRegisterFromPath(Path.of("C:", "Windows", "Fonts", "msyhbd.ttc"))) {
            return;
        }
        if (tryRegisterFromPath(Path.of("C:", "Windows", "Fonts", "simsun.ttc"))) {
            return;
        }
        if (tryRegisterFromPath(Path.of("C:", "Windows", "Fonts", "simhei.ttf"))) {
            return;
        }
        if (tryRegisterFromClasspath("/fonts/NotoSansSC-Regular.otf")) {
            return;
        }
        List<Path> linuxCandidates = List.of(
                Path.of("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"),
                Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
                Path.of("/usr/share/fonts/google-noto-cjk/NotoSansCJK-Regular.ttc"),
                Path.of("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc")
        );
        for (Path candidate : linuxCandidates) {
            if (tryRegisterFromPath(candidate)) {
                return;
            }
        }
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String name : ge.getAvailableFontFamilyNames()) {
            Font probe = new Font(name, Font.PLAIN, 12);
            if (canDisplayCjk(probe)) {
                cjkFont = probe;
                cjkFontFamily = name;
                cjkFontManager = new CjkDrawFontManager(cjkFont, cjkFontFamily);
                log.info("PPT render using system CJK font: {}", name);
                return;
            }
        }
        log.warn("PPT render: no CJK-capable font found; slide text may show as tofu blocks");
    }

    private static boolean tryRegisterFromPath(Path candidate) {
        if (!Files.isRegularFile(candidate)) {
            return false;
        }
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, candidate.toFile());
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            cjkFont = font;
            cjkFontFamily = font.getFamily();
            cjkFontManager = new CjkDrawFontManager(cjkFont, cjkFontFamily);
            log.info("PPT render fallback font registered: {} ({})", cjkFontFamily, candidate);
            return true;
        } catch (FontFormatException | IOException e) {
            log.debug("skip PPT font candidate {}: {}", candidate, e.getMessage());
            return false;
        }
    }

    private static boolean tryRegisterFromClasspath(String resourcePath) {
        try (InputStream in = PptxSlideRenderSupport.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            cjkFont = font;
            cjkFontFamily = font.getFamily();
            cjkFontManager = new CjkDrawFontManager(cjkFont, cjkFontFamily);
            log.info("PPT render fallback font registered from classpath: {}", cjkFontFamily);
            return true;
        } catch (FontFormatException | IOException e) {
            log.debug("skip classpath PPT font {}: {}", resourcePath, e.getMessage());
            return false;
        }
    }

    private static boolean canDisplayCjk(Font font) {
        return font != null && font.canDisplay('中') && font.canDisplay('文') && font.canDisplay('表');
    }

    private static final class CjkDrawFontManager extends DrawFontManagerDefault {
        private final Font baseFont;
        private final FontInfo fallbackInfo;

        private CjkDrawFontManager(Font baseFont, String family) {
            this.baseFont = baseFont;
            this.fallbackInfo = () -> family;
        }

        @Override
        public FontInfo getFallbackFont(Graphics2D graphics, FontInfo fontInfo) {
            return fallbackInfo;
        }

        @Override
        public Font createAWTFont(Graphics2D graphics, FontInfo fontInfo, double fontSize, boolean bold, boolean italic) {
            Font rendered = super.createAWTFont(graphics, fontInfo, fontSize, bold, italic);
            if (rendered != null && canDisplayCjk(rendered)) {
                return rendered;
            }
            int style = Font.PLAIN;
            if (bold) {
                style |= Font.BOLD;
            }
            if (italic) {
                style |= Font.ITALIC;
            }
            float size = (float) Math.max(1d, fontSize);
            return baseFont.deriveFont(style, size);
        }
    }
}
