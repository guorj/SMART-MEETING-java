package com.smartmeeting.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoAttachmentResponse {
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
