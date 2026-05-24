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
 * @param sourceUrl           原始配置或浏览器链接
 * @param bitableDisplayMode  来自 int_matter_progress_doc_config；null 表示 GROUPED
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
        String sourceUrl,
        /** RAW | GROUPED，仅会中 bitable 导出使用 */
        String bitableDisplayMode
) {
    public FeishuResourceRef(FeishuResourceKind kind, String primaryToken, String tableId, String viewId,
            String sourceUrl) {
        this(kind, primaryToken, tableId, viewId, sourceUrl, null);
    }
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
     * @return DOCX/WIKI/BASE 有 app_token 时可拉取；BASE 无 table 时拉取全部数据表
     */
    public boolean canFetchPlainText() {
        if (primaryToken == null || primaryToken.isBlank()) {
            return false;
        }
        return switch (kind) {
            case DOCX, WIKI, BASE -> true;
            case UNKNOWN -> false;
        };
    }

    /**
     * 主持页「在飞书中打开」链接：优先原始 URL，否则按类型拼默认域名链接。
     *
     * @return 可在浏览器打开的飞书链接；无法构造时返回 {@code null}
     */
    /**
     * 去重键：同类型、同 token/table/view 视为同一份资料（忽略域名与 query 顺序差异）。
     */
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
