package com.smartmeeting.api.dto.host;

import lombok.Data;

/**
 * 主持会序中的一项：与 DB 字段 {@code host_agenda}、主持开始请求 {@code HostStartRequest#items} 的 JSON 形态一致。
 */
@Data
public class HostAgendaItemDto {
    /** 会序标题，展示与 TTS「当前进行」等话术拼接 */
    private String title;
    /** 本项预计时长（分钟），缺省或非法时服务端按 10 处理 */
    private Integer minutes;
    /** 可选：本项补充说明（Markdown），对应 JSON {@code items[].detail}，下发主持页「当前议程」 */
    private String detail;
}
