package com.smartmeeting.matterprogress.feishu;

/** 飞书多类型读写：读 docx/wiki/base 正文 + 写 docx 报告 */
public interface FeishuDocClient {

    /**
     * 拉取飞书链接对应纯文本，按 URL 路径自动识别 docx / wiki / base 类型。
     * <ul>
     *   <li>/docx/{id} — 分页读 blocks，提取 text_run + equation</li>
     *   <li>/wiki/{id} — get_node 解析 obj_type 后递归到 docx 或 bitable</li>
     *   <li>/base/{id}?table=tbl… — bitable records/search，含 WrongTableId 自动纠正</li>
     * </ul>
     * 无法识别的 URL 返回空字符串。
     */
    String fetchPlainText(String feishuDocUrl);

    /** 创建 Docx 并写入 Markdown 正文；返回 docUrl */
    String createAndWriteMarkdown(String folderToken, String title, String markdown);
}
