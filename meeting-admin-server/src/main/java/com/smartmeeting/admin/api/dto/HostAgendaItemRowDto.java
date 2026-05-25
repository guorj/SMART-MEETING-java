package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostAgendaItemRowDto {
    private int index;
    private String title;
    private Integer minutes;
    private boolean hasRollCallKeyword;
}
