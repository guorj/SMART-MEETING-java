package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;

/**
 * 将飞书 Docx block JSON 转为带 Markdown 结构的纯文本（供会中 marked 渲染）。
 */
public final class DocxBlockMarkdownExporter {

    private static final Map<Integer, String> BLOCK_TYPE_FIELD = Map.ofEntries(
            Map.entry(2, "text"),
            Map.entry(3, "heading1"),
            Map.entry(4, "heading2"),
            Map.entry(5, "heading3"),
            Map.entry(6, "heading4"),
            Map.entry(7, "heading5"),
            Map.entry(8, "heading6"),
            Map.entry(9, "heading7"),
            Map.entry(10, "heading8"),
            Map.entry(11, "heading9"),
            Map.entry(12, "bullet"),
            Map.entry(13, "ordered"),
            Map.entry(15, "quote"),
            Map.entry(17, "todo"));

    private DocxBlockMarkdownExporter() {
    }

    public static void appendBlock(JsonNode block, StringBuilder out) {
        if (block == null || block.isNull()) {
            return;
        }
        int blockType = block.path("block_type").asInt(0);
        if (blockType == 22) {
            out.append("---\n");
            return;
        }
        String line = extractLine(block, blockType);
        if (line.isEmpty()) {
            out.append('\n');
            return;
        }
        switch (blockType) {
            case 3 -> out.append("# ").append(line);
            case 4 -> out.append("## ").append(line);
            case 5 -> out.append("### ").append(line);
            case 6 -> out.append("#### ").append(line);
            case 7, 8, 9, 10, 11 -> out.append("##### ").append(line);
            case 12 -> out.append("- ").append(line);
            case 13 -> out.append("1. ").append(line);
            case 15 -> out.append("> ").append(line);
            case 17 -> out.append("- [ ] ").append(line);
            default -> out.append(line);
        }
        out.append('\n');
    }

    private static String extractLine(JsonNode block, int blockType) {
        String field = BLOCK_TYPE_FIELD.get(blockType);
        if (field != null) {
            JsonNode node = block.get(field);
            if (node != null && node.isObject()) {
                String text = textFromElements(node.get("elements"));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        Iterator<Map.Entry<String, JsonNode>> it = block.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if ("block_id".equals(key) || "block_type".equals(key) || "parent_id".equals(key)
                    || "children".equals(key) || "comment_ids".equals(key)) {
                continue;
            }
            JsonNode val = e.getValue();
            if (val != null && val.isObject() && val.has("elements") && val.get("elements").isArray()) {
                String text = textFromElements(val.get("elements"));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String textFromElements(JsonNode elements) {
        if (elements == null || !elements.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode el : elements) {
            if (el == null || !el.isObject()) {
                continue;
            }
            if (el.has("text_run")) {
                sb.append(el.path("text_run").path("content").asText(""));
            } else if (el.has("equation")) {
                sb.append(el.path("equation").path("content").asText(""));
            }
        }
        return sb.toString().trim();
    }
}
