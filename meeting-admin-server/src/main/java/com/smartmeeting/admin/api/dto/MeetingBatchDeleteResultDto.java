package com.smartmeeting.admin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MeetingBatchDeleteResultDto {
    private int deleted;
    @Builder.Default
    private List<SkippedMeeting> skipped = new ArrayList<>();

    @Data
    @Builder
    public static class SkippedMeeting {
        private String id;
        private String reason;
    }
}
