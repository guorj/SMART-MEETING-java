package com.smartmeeting.service.feishu;

/**
 * 解析后的飞书资源引用，供 {@link com.smartmeeting.service.FeishuService} 拉取正文或主持页展示。
 * <p>
 * 主要协作组件：{@link FeishuResourceResolver}（URL 解析）、{@link FeishuResourceKind}（资源类型枚举）。
 *
 * @param kind          资源类型（DOCX / WIKI / BASE / UNKNOWN）
 * @param primaryToken  docx document_id、wiki node_token、或 base app_token
 * @param tableId       多维表格 table_id（仅 BASE）
 * @param viewId        多维表格 view_id（可选，仅 BASE）
 * @param sourceUrl     原始配置或浏览器链接
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
    /**
     * 是否配置了有效的外链 URL。
     *
     * @return sourceUrl 非空时返回 {@code true}
     */
    public boolean hasLink() {
        return sourceUrl != null && !sourceUrl.isBlank();
    }

    /**
     * 主持页是否应展示参考资料区（含仅外链、无法拉正文的类型）。
     *
     * @return 有有效 token 或可识别外链时返回 {@code true}
     */
    public boolean showOnHostPage() {
        if (kind == FeishuResourceKind.UNKNOWN) {
            return false;
        }
        if (primaryToken != null && !primaryToken.isBlank()) {
            return true;
        }
        return hasLink() && FeishuResourceResolver.isRecognizedFeishuDocUrl(sourceUrl);
    }

    /**
     * 是否可通过 API 拉取纯文本正文。
     *
     * @return DOCX/WIKI 有 primaryToken，或 BASE 有 tableId 时返回 {@code true}
     */
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

    /**
     * 主持页「在飞书中打开」链接：优先原始 URL，否则按类型拼默认域名链接。
     *
     * @return 可在浏览器打开的飞书链接；无法构造时返回 {@code null}
     */
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
