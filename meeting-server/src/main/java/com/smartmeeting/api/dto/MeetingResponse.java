package com.smartmeeting.api.dto;

import com.smartmeeting.api.dto.host.HostAgendaItemDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MeetingResponse {
    private String id;
    private String title;
    private List<String> agenda;
    private List<HostAgendaItemDto> hostAgendaItems;
    private String company;
    private String department;
    private String groupName;
    private Integer presetTypeCode;
    private String status;
    private String creatorId;
    private String chatId;
    private String roomId;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    private String docUrl;
    private String recordingUrl;
    private List<ParticipantDTO> participants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ParticipantDTO {
        private String userId;
        private String name;
        private String status;
    }
}
