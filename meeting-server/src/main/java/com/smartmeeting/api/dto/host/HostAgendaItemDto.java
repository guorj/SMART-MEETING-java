package com.smartmeeting.api.dto.host;

import com.smartmeeting.api.dto.FeishuDocRefDto;
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
}
