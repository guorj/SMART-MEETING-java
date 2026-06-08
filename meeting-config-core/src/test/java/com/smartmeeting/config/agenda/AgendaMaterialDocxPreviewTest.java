package com.smartmeeting.config.agenda;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgendaMaterialDocxPreviewTest {

    @Test
    void paragraphBreaksPreservedInPlainText() {
        String xml = """
                <w:document><w:body>
                <w:p><w:r><w:t>第一段</w:t></w:r></w:p>
                <w:p><w:r><w:t>第二段</w:t></w:r></w:p>
                </w:body></w:document>
                """;
        AgendaMaterialDocxPreview.DocxPreview preview = AgendaMaterialDocxPreview.buildPreview(xml, Map.of(), Map.of());
        assertTrue(preview.plainText().contains("第一段"));
        assertTrue(preview.plainText().contains("第二段"));
        assertTrue(preview.plainText().contains("\n"));
        assertTrue(preview.html().contains("<p>"));
    }

    @Test
    void embeddedImageRenderedAsDataUrl() {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        String xml = """
                <w:document><w:body>
                <w:p><w:r><w:drawing><a:blip r:embed="rId5"/></w:drawing></w:r></w:p>
                </w:body></w:document>
                """;
        Map<String, String> rels = Map.of("rId5", "media/image1.png");
        Map<String, byte[]> media = Map.of("image1.png", png);
        AgendaMaterialDocxPreview.DocxPreview preview = AgendaMaterialDocxPreview.buildPreview(xml, rels, media);
        assertTrue(preview.html().contains("data:image/png;base64,"));
        assertTrue(preview.html().contains("local-doc-embed-img"));
    }

    @Test
    void lineBreakInsideParagraph() {
        String xml = """
                <w:document><w:body>
                <w:p><w:r><w:t>行1</w:t></w:r><w:br/><w:r><w:t>行2</w:t></w:r></w:p>
                </w:body></w:document>
                """;
        AgendaMaterialDocxPreview.DocxPreview preview = AgendaMaterialDocxPreview.buildPreview(xml, Map.of(), Map.of());
        assertTrue(preview.html().contains("<br/>"));
        assertTrue(preview.plainText().contains("行1"));
        assertTrue(preview.plainText().contains("行2"));
    }

    @Test
    void legacyFormatPlainTextHelper() {
        String xml = """
                <w:document><w:body><w:p><w:r><w:t>方案</w:t></w:r><w:r><w:t>评估</w:t></w:r></w:p></w:body></w:document>
                """;
        String text = AgendaMaterialDocxPreview.formatPlainText(xml);
        assertFalse(text.isEmpty());
        assertTrue(text.contains("方案"));
    }
}
