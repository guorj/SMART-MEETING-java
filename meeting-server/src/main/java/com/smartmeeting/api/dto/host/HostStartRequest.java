package com.smartmeeting.api.dto.host;

import lombok.Data;

import java.util.List;

@Data
public class HostStartRequest {
    private Integer schemaVersion;
    private Integer totalDurationMinutes;
    private List<HostAgendaItemDto> items;
}
