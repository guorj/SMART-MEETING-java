package com.smartmeeting.service.feishu;

/**
 * 飞书云资源类型（由 URL 路径或配置推断）。
 */
public enum FeishuResourceKind {
    /** 云文档 /docx/{document_id} */
    DOCX,
    /** 知识库 /wiki/{node_token}，多数正文为 docx 载体 */
    WIKI,
    /** 多维表格 /base/{app_token}?table=... */
    BASE,
    /** 已配置 URL/token 但无法识别类型 */
    UNKNOWN
}
