package com.smartmeeting.admin.api.dto;

import lombok.Data;

@Data
public class MeetingAdminDetailDto {
    private MeetingAdminSummaryDto summary;
    private MeetingAdminFullDto meeting;
    /** @deprecated 使用 meeting.hostAgendaJson */
    private String hostAgendaJson;
    private MeetingAdminLinksDto links;
    private java.util.List<ParticipantAdminDetailDto> participants;
}
