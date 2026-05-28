package com.smartmeeting.event;

import java.util.List;

public record MeetingEndedEvent(
        String meetingId,
        String audioPath,
        List<String> featureIds,
        String modelName,
        long sentAt
) {
}
