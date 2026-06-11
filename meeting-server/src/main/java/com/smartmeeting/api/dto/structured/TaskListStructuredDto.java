package com.smartmeeting.api.dto.structured;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 飞书任务清单结构化输出（Task v2 API）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListStructuredDto {
    private String tasklistGuid;
    private String tasklistName;
    private int totalTasks;
    private String openUrl;
    private List<TaskListItemDto> items;
}
