package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingTodoProgressDto {
    private String id;
    private String todoId;
    private String authorId;
    private String authorName;
    private String authorRole;
    private String progressText;
    private Integer progressPercent;
    private LocalDateTime createdAt;
}
