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
public class TodoProgressResponse {
    private String id;
    private String todoId;
    private String authorId;
    private String authorName;
    private String authorRole;
    private String progressText;
    private Integer progressPercent;
    private LocalDateTime createdAt;
}
