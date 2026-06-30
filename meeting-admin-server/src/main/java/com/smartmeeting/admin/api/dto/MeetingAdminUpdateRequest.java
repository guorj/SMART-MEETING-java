package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class MeetingAdminUpdateRequest {
    private String title;
    private List<String> agenda;
    private String hostAgendaJson;
    private String company;
    private String department;
    private String groupName;
    private String status;
    private String creatorId;
    private String chatId;
    private String meetingScenario;
    private String previousMeetingId;
    private String sourceAudioUrl;
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String vcMinuteToken;
}
