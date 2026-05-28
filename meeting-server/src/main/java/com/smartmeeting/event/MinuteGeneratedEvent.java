package com.smartmeeting.event;

public record MinuteGeneratedEvent(
        String meetingId,
        long sentAt
) {
}
