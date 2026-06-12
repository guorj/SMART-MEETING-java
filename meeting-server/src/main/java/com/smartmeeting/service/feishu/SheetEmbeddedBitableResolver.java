package com.smartmeeting.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 从电子表格 metainfo 解析内嵌多维表格（blockType=BITABLE_BLOCK）。
 * 飞书 FAQ：{@code blockInfo.blockToken} 格式为 {@code {appToken}_{tableId}}。
 */
public final class SheetEmbeddedBitableResolver {

    private SheetEmbeddedBitableResolver() {
    }

    public static List<DocxEmbeddedBitableResolver.EmbeddedBitableRef> listEmbeddedBitables(JsonNode metainfo) {
        List<DocxEmbeddedBitableResolver.EmbeddedBitableRef> out = new ArrayList<>();
        if (metainfo == null) {
            return out;
        }
        JsonNode sheets = metainfo.path("sheets");
        if (!sheets.isArray()) {
            return out;
        }
        for (JsonNode sheet : sheets) {
            JsonNode blockInfo = sheet.path("blockInfo");
            if (!"BITABLE_BLOCK".equalsIgnoreCase(blockInfo.path("blockType").asText(""))) {
                continue;
            }
            DocxEmbeddedBitableResolver.EmbeddedBitableRef parsed =
                    DocxEmbeddedBitableResolver.parseCombinedToken(blockInfo.path("blockToken").asText(""));
            if (parsed != null) {
                out.add(new DocxEmbeddedBitableResolver.EmbeddedBitableRef(
                        parsed.appToken(), parsed.tableId(), sheet.path("sheetId").asText("")));
            }
        }
        return out;
    }

    /**
     * 按 URL 配置的 tableId 匹配嵌入块；唯一嵌入块时允许 configuredTableId 为空。
     */
    public static DocxEmbeddedBitableResolver.EmbeddedBitableRef findEmbeddedBitable(
            JsonNode metainfo, String configuredTableId) {
        List<DocxEmbeddedBitableResolver.EmbeddedBitableRef> all = listEmbeddedBitables(metainfo);
        if (all.isEmpty()) {
            return null;
        }
        String want = configuredTableId != null ? configuredTableId.trim() : "";
        if (!want.isEmpty()) {
            List<String> available = all.stream().map(DocxEmbeddedBitableResolver.EmbeddedBitableRef::tableId).toList();
            String resolved = BitableTableIdResolver.resolveFromListing(want, available);
            if (resolved != null) {
                for (DocxEmbeddedBitableResolver.EmbeddedBitableRef ref : all) {
                    if (resolved.equals(ref.tableId())) {
                        return ref;
                    }
                }
            }
            for (DocxEmbeddedBitableResolver.EmbeddedBitableRef ref : all) {
                if (want.equals(ref.tableId())) {
                    return ref;
                }
            }
            return null;
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        return null;
    }
}
