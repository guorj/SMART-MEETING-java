package com.smartmeeting.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoAssignRequest {
    @NotBlank(message = "assigneeId 不能为空")
    private String assigneeId;
    /** 可选；不传则保留原姓名 */
    private String assigneeName;
}
