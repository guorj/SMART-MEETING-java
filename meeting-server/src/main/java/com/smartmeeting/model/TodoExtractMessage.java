package com.smartmeeting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoExtractMessage {
    private String meetingId;
    private String minuteText;
    private List<ParticipantInfo> participants;
    private Long sentAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantInfo {
        private String userId;
        private String name;
    }
}
