package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class MeetingAdminDetailDto {
    private MeetingAdminSummaryDto summary;
    private String hostAgendaJson;
    private MeetingAdminLinksDto links;
    private java.util.List<ParticipantSummaryDto> participants;
}
