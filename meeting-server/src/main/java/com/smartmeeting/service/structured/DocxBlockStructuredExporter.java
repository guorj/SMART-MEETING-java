package com.smartmeeting.service.structured;
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
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String id = item.path("block_id").asText("");
            if (!id.isEmpty()) {
                byId.put(id, item);
            }
        }
        for (JsonNode item : items) {
            if (item.path("block_type").asInt(0) == 1) {
                List<DocxBlockDto> roots = new ArrayList<>();
                for (JsonNode childId : item.path("children")) {
                    JsonNode child = byId.get(childId.asText(""));
                    if (child != null) {
                        DocxBlockDto tree = exportBlockTree(child, byId);
                        if (tree != null) {
                            roots.add(tree);
                        }
                    }
                }
                if (!roots.isEmpty()) {
                    return roots;
                }
            }
        }
        List<DocxBlockDto> flat = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.path("block_type").asInt(0) == 1) {
                continue;
            }
            DocxBlockDto block = exportBlock(item);
            if (block != null) {
                flat.add(block);
            }
        }
        return flat;
    }

    private static DocxBlockDto exportBlockTree(JsonNode block, Map<String, JsonNode> byId) {
        DocxBlockDto base = exportBlock(block);
        if (base == null) {
            return null;
        }
        List<DocxBlockDto> childDtos = new ArrayList<>();
        for (JsonNode childId : block.path("children")) {
            JsonNode child = byId.get(childId.asText(""));
            if (child != null) {
                DocxBlockDto nested = exportBlockTree(child, byId);
                if (nested != null) {
                    childDtos.add(nested);
                }
            }
        }
        if (childDtos.isEmpty()) {
            return base;
        }
        return DocxBlockDto.builder()
                .type(base.getType())
                .text(base.getText())
                .runs(base.getRuns())
                .imageKey(base.getImageKey())
                .checked(base.getChecked())
                .children(childDtos)
                .build();
    }

    public static DocxBlockDto exportBlock(JsonNode block) {
        if (block == null || block.isNull()) return null;
        int blockType = block.path("block_type").asInt(0);
        if (blockType == 22) return DocxBlockDto.builder().type("divider").build();
        if (blockType == 23) {
            String key = block.path("image").path("token").asText("");
            return DocxBlockDto.builder().type("image").imageKey(key.isEmpty() ? null : key).build();
        }
        if (blockType == 14) {
            JsonNode codeNode = block.get("code");
            if (codeNode == null || !codeNode.isObject()) {
                codeNode = findFirstElementsObject(block);
            }
            String text = "";
            if (codeNode != null && codeNode.has("elements")) {
                text = extractRuns(codeNode.get("elements")).text;
            }
            return DocxBlockDto.builder().type("code").text(text).build();
        }
        if (blockType == 19) {
            JsonNode callout = block.get("callout");
            if (callout == null || !callout.isObject()) {
                callout = findFirstElementsObject(block);
            }
            String text = "";
            List<RichTextRunDto> runs = List.of();
            if (callout != null && callout.has("elements")) {
                var result = extractRuns(callout.get("elements"));
                text = result.text;
                runs = result.runs;
            }
            return DocxBlockDto.builder().type("callout").text(text).runs(runs).build();
        }
        if (blockType == 31) {
            return DocxBlockDto.builder().type("table").tableRows(exportTableRows(block)).build();
        }
        if (blockType == 32) {
            JsonNode cell = block.get("table_cell");
            if (cell == null || !cell.isObject()) {
                cell = findFirstElementsObject(block);
            }
            String text = cell != null && cell.has("elements")
                    ? extractRuns(cell.get("elements")).text : "";
            return DocxBlockDto.builder().type("table_cell").text(text).build();
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
            case 17 -> "todo"; case 19 -> "callout"; default -> "paragraph";
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

    private static List<List<String>> exportTableRows(JsonNode tableBlock) {
        List<List<String>> rows = new ArrayList<>();
        JsonNode table = tableBlock.get("table");
        if (table != null && table.isObject() && table.has("cells")) {
            int cols = Math.max(1, table.path("property").path("column_size").asInt(1));
            List<String> flat = new ArrayList<>();
            for (JsonNode cell : table.path("cells")) {
                String text = cell.has("text") ? cell.path("text").asText("") : "";
                flat.add(text.trim());
            }
            for (int i = 0; i < flat.size(); i += cols) {
                List<String> row = new ArrayList<>();
                for (int c = 0; c < cols && i + c < flat.size(); c++) {
                    row.add(flat.get(i + c));
                }
                rows.add(row);
            }
            return SheetStructuredExporter.trimTrailingEmptyColumns(rows);
        }
        return rows;
    }
}
