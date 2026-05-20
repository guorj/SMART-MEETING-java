package com.smartmeeting.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 待办责任人指派请求体（{@code PUT /api/v1/todos/{tid}/assign}）。
 */
@Data
public class TodoAssignRequest {
    @NotBlank(message = "assigneeId 不能为空")
    private String assigneeId;
    /** 可选；不传则保留原姓名 */
    private String assigneeName;
}
