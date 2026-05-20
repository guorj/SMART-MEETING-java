package com.smartmeeting.service.feishu;

/**
 * 飞书云资源类型（由 URL 路径或配置推断）。
 *
 * <p>与 {@link FeishuResourceResolver}、{@link FeishuDocRefs#kindLabel(FeishuResourceKind)} 配合使用，
 * 区分 Docx 正文拉取、Wiki 节点与多维表格等不同接入方式。
 */
public enum FeishuResourceKind {
    /** 云文档，路径形如 {@code /docx/{document_id}} */
    DOCX,
    /** 知识库节点，路径形如 {@code /wiki/{node_token}}，多数正文为 docx 载体 */
    WIKI,
    /** 多维表格，路径形如 {@code /base/{app_token}?table=...} */
    BASE,
    /** 已配置 URL 或 token 但无法识别为上述类型 */
    UNKNOWN
}
