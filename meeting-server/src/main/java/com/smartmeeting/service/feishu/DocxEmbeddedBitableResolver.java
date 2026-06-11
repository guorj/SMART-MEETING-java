package com.smartmeeting.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Docx 文档块中解析内嵌多维表格（block_type=18）。
 * 飞书 FAQ：{@code bitable.token} 格式为 {@code {appToken}_{tableId}}。
 */
public final class DocxEmbeddedBitableResolver {

    public record EmbeddedBitableRef(String appToken, String tableId, String blockId) {
    }

    private DocxEmbeddedBitableResolver() {
    }

    public static EmbeddedBitableRef parseCombinedToken(String combined) {
        if (combined == null || combined.isBlank()) {
            return null;
        }
        int idx = combined.indexOf("_tbl");
        if (idx <= 0 || idx >= combined.length() - 4) {
            return null;
        }
        String appToken = combined.substring(0, idx);
        String tableId = combined.substring(idx + 1);
        if (appToken.isBlank() || tableId.isBlank()) {
            return null;
        }
        return new EmbeddedBitableRef(appToken, tableId, null);
    }

    public static List<EmbeddedBitableRef> listEmbeddedBitables(JsonNode items) {
        List<EmbeddedBitableRef> out = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return out;
        }
        for (JsonNode item : items) {
            if (item == null || item.path("block_type").asInt(0) != 18) {
                continue;
            }
            String combined = item.path("bitable").path("token").asText("");
            EmbeddedBitableRef parsed = parseCombinedToken(combined);
            if (parsed != null) {
                out.add(new EmbeddedBitableRef(
                        parsed.appToken(), parsed.tableId(), item.path("block_id").asText("")));
            }
        }
        return out;
    }

    /**
     * 按 URL 配置的 tableId 匹配嵌入块；唯一嵌入块时允许 configuredTableId 为空。
     */
    public static EmbeddedBitableRef findEmbeddedBitable(JsonNode items, String configuredTableId) {
        List<EmbeddedBitableRef> all = listEmbeddedBitables(items);
        if (all.isEmpty()) {
            return null;
        }
        String want = configuredTableId != null ? configuredTableId.trim() : "";
        if (!want.isEmpty()) {
            List<String> available = all.stream().map(EmbeddedBitableRef::tableId).toList();
            String resolved = BitableTableIdResolver.resolveFromListing(want, available);
            if (resolved != null) {
                for (EmbeddedBitableRef ref : all) {
                    if (resolved.equals(ref.tableId())) {
                        return ref;
                    }
                }
            }
            for (EmbeddedBitableRef ref : all) {
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
