package com.smartmeeting.constants;

/**
 * AI 主持议程常量。
 * <p>
 * 代码内保底 JSON，与 {@code int_meeting_type_preset} 种子 host_agenda 建议保持一致。
 */
public final class HostAgendaConstants {

    private HostAgendaConstants() {
    }

    /**
     * 当库中 preset.host_agenda 与会议 host_agenda 均为空时的硬编码保底 JSON。
     * <p>
     * 须与 schema 种子、前端约定一致；会序 2 为「事项进度通报」。
     */
    public static final String DEFAULT_HOST_AGENDA_JSON =
            "{\"items\":[{\"title\":\"主持议题A\",\"minutes\":3,\"detail\":\"- 开场与流程说明\\n- 注意节奏与时间\"},{\"title\":\"事项进度通报\",\"minutes\":7,\"detail\":\"下方「事项进度通报」卡片将展示文档全文；主持语音仍由议程播报驱动。\"}]}";
}
