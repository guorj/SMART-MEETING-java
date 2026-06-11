package com.smartmeeting.api.dto.structured;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListItemDto {
    private String taskGuid;
    private String summary;
    /** completed / in_progress */
    private String status;
    private String dueAt;
    private String completedAt;
    private String assignees;
}
