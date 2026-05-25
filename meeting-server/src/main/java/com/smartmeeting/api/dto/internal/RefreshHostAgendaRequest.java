package com.smartmeeting.api.dto.internal;

import lombok.Data;

import java.util.List;

@Data
public class RefreshHostAgendaRequest {
    private Integer presetTypeCode;
    private boolean dryRun = true;
    /** 为空则刷新该 preset 下所有可刷新状态的会议 */
    private List<String> meetingIds;
}
