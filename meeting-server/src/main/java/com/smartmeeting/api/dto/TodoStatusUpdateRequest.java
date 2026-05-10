package com.smartmeeting.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoStatusUpdateRequest {
    /** {@link com.smartmeeting.enums.TodoStatus} 枚举名，如 PENDING、IN_PROGRESS、COMPLETED */
    @NotBlank(message = "status 不能为空")
    private String status;
    /** 完成时可选填写 */
    private String completionNote;
    /** 延期/卡点时可选填写 */
    private String blockReason;
}
