package com.smartmeeting.admin.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MeetingTodoAttachmentDto {
    private String id;
    private String todoId;
    private String progressId;
    private String uploaderId;
    private String uploaderName;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime createdAt;
}
