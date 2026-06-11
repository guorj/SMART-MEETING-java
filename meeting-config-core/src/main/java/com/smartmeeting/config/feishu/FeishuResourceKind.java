package com.smartmeeting.config.feishu;

/**
 * 飞书云资源类型（由 URL 路径或配置推断）。
 */
public enum FeishuResourceKind {
    DOCX,
    WIKI,
    BASE,
    /** 飞书电子表格直链 {@code /sheets/} */
    SHEET,
    /** 飞书任务清单 AppLink {@code applink.../client/todo/task_list?guid=} */
    TASKLIST,
    UNKNOWN
}
