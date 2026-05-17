package com.smartmeeting.service.feishu;

/**
 * 解析后的飞书资源引用，供拉取正文或下发主持页。
 */
public record FeishuResourceRef(
        FeishuResourceKind kind,
        /** docx document_id、wiki node_token、或 base app_token */
        String primaryToken,
        /** 多维表格 table_id（仅 BASE） */
        String tableId,
        /** 多维表格 view_id（可选，仅 BASE） */
        String viewId,
        /** 原始配置或浏览器链接 */
        String sourceUrl
) {
    public boolean hasLink() {
        return sourceUrl != null && !sourceUrl.isBlank();
    }

    /** 主持页是否应展示参考资料区（含仅外链、无法拉正文的类型） */
    public boolean showOnHostPage() {
        if (kind == FeishuResourceKind.UNKNOWN) {
            return false;
        }
        if (primaryToken != null && !primaryToken.isBlank()) {
            return true;
        }
        return hasLink() && FeishuResourceResolver.isRecognizedFeishuDocUrl(sourceUrl);
    }

    public boolean canFetchPlainText() {
        if (primaryToken == null || primaryToken.isBlank()) {
            return false;
        }
        return switch (kind) {
            case DOCX, WIKI -> true;
            case BASE -> tableId != null && !tableId.isBlank();
            case UNKNOWN -> false;
        };
    }

    /** 主持页「在飞书中打开」链接：优先原始 URL，否则按类型拼默认域名链接 */
    public String defaultOpenUrl() {
        if (hasLink()) {
            return sourceUrl.trim();
        }
        if (primaryToken == null || primaryToken.isBlank()) {
            return null;
        }
        return switch (kind) {
            case DOCX -> "https://bytedance.feishu.cn/docx/" + primaryToken;
            case WIKI -> "https://bytedance.feishu.cn/wiki/" + primaryToken;
            case BASE -> {
                StringBuilder sb = new StringBuilder("https://bytedance.feishu.cn/base/").append(primaryToken);
                if (tableId != null && !tableId.isBlank()) {
                    sb.append("?table=").append(tableId);
                    if (viewId != null && !viewId.isBlank()) {
                        sb.append("&view=").append(viewId);
                    }
                }
                yield sb.toString();
            }
            default -> null;
        };
    }
}
