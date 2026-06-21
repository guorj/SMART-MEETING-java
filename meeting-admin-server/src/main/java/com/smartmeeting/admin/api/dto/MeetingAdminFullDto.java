package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingAdminFullDto {
    private String id;
    private String title;
    private String agendaJson;
    private String hostAgendaJson;
    private String company;
    private String department;
    private String groupName;
    private Integer presetTypeCode;
    private String status;
    private String creatorId;
    private String chatId;
    /** 飞书日历 event_id（历史字段名 roomId） */
    private String roomId;
    private String meetingScenario;
    private String sourceAudioUrl;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String recordingUrl;
    private String recordingToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
