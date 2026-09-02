package com.smartmeeting.api.dto.host;

import com.smartmeeting.api.dto.FeishuDocRefDto;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import lombok.Data;

import java.util.List;

/**
 * AI 主持会序中的一项议题 DTO。
 * <p>
 * 与 DB 字段 {@code host_agenda}、主持开始请求 {@link HostStartRequest#getItems()} 的 JSON 形态一致。
 *
 * @see FeishuDocRefDto
 */
@Data
public class HostAgendaItemDto {
    /** 会序标题，展示与 TTS「当前进行」等话术拼接 */
    private String title;
    /** 本项预计时长（分钟），缺省或非法时服务端按 10 处理 */
    private Integer minutes;
    /** 可选：本项补充说明（Markdown），对应 JSON {@code items[].detail}，下发主持页「当前议程」 */
    private String detail;
    /** 可选：飞书资料链接（docx/wiki/base）；可与 int_matter_progress_doc_config 按 preset+会序合并 */
    private String feishuDocUrl;
    /** 可选：同一会序多条资料（base/docx/wiki），优先级高于单条 feishuDocUrl */
    private List<FeishuDocRefDto> feishuDocs;
    /** 可选：oabp 库只读 SQL（jq_project_task_tracking 等），会序资料区表格展示 */
    private String oabpTaskSql;
    /** 可选：是否在主持页展示 oabp 查询结果；默认 true */
    private Boolean oabpTaskShow;
    /** 可选：oabp 结果集展示模板；为空时直通 raw sheet（向后兼容） */
    private OabpDisplayTemplate oabpDisplayTemplate;
    /** 可选：oabp SQL 预设 id；custom 或 null 表示手写 SQL */
    private String oabpSqlPresetId;
    /** 可选：true 时严格按 SQL 结果展示，不应用展示模板与额外渲染 */
    private Boolean oabpTaskSqlStrict;
    /** 可选：会序外链 URL（如 OA 分析页），主持页「当前议程」展示为「↗ 打开 XXX」按钮，新窗口跳转 */
    private String externalUrl;
    /** 可选：外链按钮文案，缺省时按 URL 自动生成 */
    private String externalLinkLabel;
}
