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
public class MinuteGenerateMessage {
    private String meetingId;
    private String audioPath;
    private List<String> featureIds;
    private String modelName;
    private Long sentAt;
}
