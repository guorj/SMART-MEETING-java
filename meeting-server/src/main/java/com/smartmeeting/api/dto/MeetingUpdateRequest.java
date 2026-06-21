package com.smartmeeting.api.dto;

import lombok.Data;

import java.util.List;

/** Admin / 内部接口：会议字段更新（不含 scheduledTime，改期走 schedule API）。 */
@Data
public class MeetingUpdateRequest {
    private String title;
    private List<String> agenda;
    /** host_agenda JSON 字符串 */
    private String hostAgendaJson;
    private String company;
    private String department;
    private String groupName;
    /** 仅允许安全值如 CANCELLED */
    private String status;
    private String creatorId;
    private String chatId;
    private String meetingScenario;
    private String previousMeetingId;
    private String sourceAudioUrl;
    private String audioPath;
    private String docUrl;
    private String docToken;
}
