package com.smartmeeting.config.agenda;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从 docx（OOXML zip）提取 HTML 预览：保留段落/换行，并内嵌 word/media 图片。
 */
public final class AgendaMaterialDocxPreview {

    private static final Pattern WT_TEXT = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
    private static final Pattern PARA_BLOCK = Pattern.compile("<w:p[\\s>][\\s\\S]*?</w:p>");
    private static final Pattern EMBED = Pattern.compile("r:embed=\"([^\"]+)\"");
    private static final Pattern REL = Pattern.compile("Id=\"([^\"]+)\"[^>]*Target=\"([^\"]+)\"");
    private static final Pattern BR_TAG = Pattern.compile("<w:br[^>]*/>");

    private AgendaMaterialDocxPreview() {
    }

    public record DocxPreview(String html, String plainText) {
    }

    public static DocxPreview extractPreview(Path docxPath) throws IOException {
        if (docxPath == null || !Files.isRegularFile(docxPath)) {
            return new DocxPreview("", "");
        }
        Map<String, byte[]> media = new HashMap<>();
        Map<String, String> rels = new HashMap<>();
        String documentXml = "";
        try (InputStream in = Files.newInputStream(docxPath);
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/document.xml".equals(name)) {
                    documentXml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                } else if ("word/_rels/document.xml.rels".equals(name)) {
                    parseRelationships(new String(zis.readAllBytes(), StandardCharsets.UTF_8), rels);
                } else if (name.startsWith("word/media/") && !name.endsWith("/")) {
                    media.put(name.substring("word/media/".length()), zis.readAllBytes());
                }
            }
        }
        return buildPreview(documentXml, rels, media);
    }

    /** @deprecated 使用 {@link #extractPreview(Path)} */
    public static String extractPlainText(Path docxPath) throws IOException {
        return extractPreview(docxPath).plainText();
    }

    static DocxPreview buildPreview(String documentXml, Map<String, String> rels, Map<String, byte[]> media) {
        if (documentXml == null || documentXml.isBlank()) {
            return new DocxPreview("", "");
        }
        StringBuilder html = new StringBuilder("<div class=\"local-doc-body\">");
        StringBuilder plain = new StringBuilder();
        Matcher pm = PARA_BLOCK.matcher(documentXml);
        while (pm.find()) {
            String para = pm.group();
            String paraHtml = renderParagraphHtml(para, rels, media);
            if (paraHtml.isEmpty()) {
                continue;
            }
            html.append(paraHtml);
            String paraPlain = renderParagraphPlain(para);
            if (!paraPlain.isEmpty()) {
                if (plain.length() > 0) {
                    plain.append('\n');
                }
                plain.append(paraPlain);
            }
        }
        appendUnreferencedMedia(html, media, rels);
        html.append("</div>");
        return new DocxPreview(html.toString(), plain.toString().trim());
    }

    static String formatPlainText(String documentXml) {
        return buildPreview(documentXml, Map.of(), Map.of()).plainText();
    }

    private static void parseRelationships(String relsXml, Map<String, String> rels) {
        if (relsXml == null || relsXml.isBlank()) {
            return;
        }
        Matcher m = REL.matcher(relsXml);
        while (m.find()) {
            rels.put(m.group(1), m.group(2));
        }
    }

    private static String renderParagraphHtml(String para, Map<String, String> rels, Map<String, byte[]> media) {
        StringBuilder text = new StringBuilder();
        String[] segments = BR_TAG.split(para);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                text.append("<br/>");
            }
            Matcher tm = WT_TEXT.matcher(segments[i]);
            while (tm.find()) {
                text.append(escapeHtml(tm.group(1)));
            }
        }
        StringBuilder imgs = new StringBuilder();
        Matcher em = EMBED.matcher(para);
        while (em.find()) {
            imgs.append(toEmbeddedImgTag(em.group(1), rels, media));
        }
        if (text.length() == 0 && imgs.length() == 0) {
            return "";
        }
        return "<p>" + text + imgs + "</p>";
    }

    private static String renderParagraphPlain(String para) {
        StringBuilder sb = new StringBuilder();
        String[] segments = BR_TAG.split(para);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            Matcher tm = WT_TEXT.matcher(segments[i]);
            while (tm.find()) {
                String t = tm.group(1);
                if (t != null && !t.isEmpty()) {
                    sb.append(t);
                }
            }
        }
        return sb.toString().trim();
    }

    private static String toEmbeddedImgTag(String relId, Map<String, String> rels, Map<String, byte[]> media) {
        if (relId == null || relId.isBlank()) {
            return "";
        }
        String target = rels.get(relId);
        if (target == null || target.isBlank()) {
            return "";
        }
        String mediaName = normalizeMediaTarget(target);
        byte[] bytes = media.get(mediaName);
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String mime = mimeFromFilename(mediaName);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return "<img class=\"local-doc-embed-img\" src=\"data:" + mime + ";base64," + b64 + "\" alt=\"\"/>";
    }

    private static void appendUnreferencedMedia(StringBuilder html, Map<String, byte[]> media, Map<String, String> rels) {
        if (media.isEmpty()) {
            return;
        }
        var referenced = new java.util.HashSet<String>();
        for (String target : rels.values()) {
            referenced.add(normalizeMediaTarget(target));
        }
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<String, byte[]> e : media.entrySet()) {
            if (referenced.contains(e.getKey()) || !isImageFilename(e.getKey())) {
                continue;
            }
            extra.append(toDataUrlImg(e.getKey(), e.getValue()));
        }
        if (extra.length() == 0) {
            return;
        }
        html.append("<div class=\"local-doc-extra-media\">").append(extra).append("</div>");
    }

    private static String toDataUrlImg(String mediaName, byte[] bytes) {
        String mime = mimeFromFilename(mediaName);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return "<img class=\"local-doc-embed-img\" src=\"data:" + mime + ";base64," + b64 + "\" alt=\"\"/>";
    }

    private static String normalizeMediaTarget(String target) {
        String t = target.replace('\\', '/').trim();
        if (t.startsWith("/")) {
            t = t.substring(1);
        }
        if (t.startsWith("word/media/")) {
            t = t.substring("word/media/".length());
        } else if (t.startsWith("media/")) {
            t = t.substring("media/".length());
        }
        return t;
    }

    private static boolean isImageFilename(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private static String mimeFromFilename(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
