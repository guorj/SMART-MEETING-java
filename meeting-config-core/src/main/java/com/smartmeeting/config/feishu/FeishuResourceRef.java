package com.smartmeeting.config.feishu;

/**
 * 解析后的飞书资源引用（配置域模型，无 Spring / HTTP 依赖）。
 */
public record FeishuResourceRef(
        FeishuResourceKind kind,
        String primaryToken,
        String tableId,
        String viewId,
        String sourceUrl,
        String bitableDisplayMode) {

    public FeishuResourceRef(FeishuResourceKind kind, String primaryToken, String tableId, String viewId,
            String sourceUrl) {
        this(kind, primaryToken, tableId, viewId, sourceUrl, null);
    }

    public boolean hasLink() {
        return sourceUrl != null && !sourceUrl.isBlank();
    }

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

    public String dedupeKey() {
        if (kind == null || kind == FeishuResourceKind.UNKNOWN) {
            return sourceUrl != null ? sourceUrl.trim() : "";
        }
        String token = primaryToken != null ? primaryToken.trim() : "";
        String table = tableId != null ? tableId.trim() : "";
        String view = viewId != null ? viewId.trim() : "";
        return kind.name() + "|" + token + "|" + table + "|" + view;
    }

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
