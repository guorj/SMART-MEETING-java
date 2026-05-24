package com.smartmeeting.matterprogress.model;

import java.time.Instant;

/** 纪要快照（JOIN int_meeting 标题） */
public record MinuteSnapshot(
        String meetingId,
        String title,
        Integer presetTypeCode,
        Instant generatedAt,
        String contentMarkdown,
        String contentUrl,
        String generationStatus
) {
}
