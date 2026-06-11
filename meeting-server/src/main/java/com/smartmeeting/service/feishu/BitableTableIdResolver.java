package com.smartmeeting.service.feishu;

import java.util.List;
import java.util.Locale;

/**
 * Bitable table_id 纠正：配置与飞书 API 列表不一致时（大小写、末尾笔误）自动匹配唯一候选。
 */
public final class BitableTableIdResolver {

    private BitableTableIdResolver() {
    }

    public static boolean isWrongTableIdError(Throwable e) {
        String msg = e != null ? e.getMessage() : null;
        return msg != null && msg.contains("1254004") && msg.contains("WrongTableId");
    }

    /**
     * 从可用 table_id 列表中解析与配置值对应的 id。
     * 顺序：精确匹配 → 大小写不敏感唯一匹配 → 前缀唯一匹配（覆盖末尾多字符笔误）。
     */
    public static String resolveFromListing(String configured, List<String> available) {
        if (configured == null || configured.isBlank() || available == null || available.isEmpty()) {
            return null;
        }
        if (available.contains(configured)) {
            return configured;
        }
        String want = configured.toLowerCase(Locale.ROOT);
        String caseMatch = null;
        for (String id : available) {
            if (id != null && id.toLowerCase(Locale.ROOT).equals(want)) {
                if (caseMatch != null) {
                    return null;
                }
                caseMatch = id;
            }
        }
        if (caseMatch != null) {
            return caseMatch;
        }
        String prefixMatch = null;
        for (String id : available) {
            if (id == null || id.isBlank()) {
                continue;
            }
            if (configured.startsWith(id) || id.startsWith(configured)) {
                if (prefixMatch != null) {
                    return null;
                }
                prefixMatch = id;
            }
        }
        return prefixMatch;
    }
}
