package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranscriptLineDto {
    private String id;
    private String speakerName;
    private Integer startTimeMs;
    private String text;
    private boolean isFinal;
    private Double confidence;
}
