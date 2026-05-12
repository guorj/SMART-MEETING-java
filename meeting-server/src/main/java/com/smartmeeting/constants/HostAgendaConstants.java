package com.smartmeeting.constants;

/**
 * AI 主持议程：代码内保底 JSON（与 int_meeting_type_preset 种子 host_agenda 建议保持一致）。
 */
public final class HostAgendaConstants {

    private HostAgendaConstants() {
    }

    /**
     * 当库中 preset.host_agenda 与会议 host_agenda 均为空时的硬编码保底（须与 schema 种子、前端约定一致）。
     */
    public static final String DEFAULT_HOST_AGENDA_JSON =
            "{\"items\":[{\"title\":\"主持议题A\",\"minutes\":3},{\"title\":\"主持议题B\",\"minutes\":7}]}";
}
