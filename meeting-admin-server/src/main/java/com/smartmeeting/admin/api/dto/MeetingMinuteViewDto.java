package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MeetingMinuteViewDto {
    private String meetingId;
    private boolean found;
    private String generationStatus;
    private String contentUrl;
    private Integer contentLength;
    private String contentMarkdown;
    private LocalDateTime generatedAt;
}
