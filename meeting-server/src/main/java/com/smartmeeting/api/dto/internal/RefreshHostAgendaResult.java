package com.smartmeeting.api.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RefreshHostAgendaResult {
    private boolean dryRun;
    private int count;
    private List<String> meetingIds;
    private boolean enriched;
    private String note;
}
