package com.smartmeeting.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoProgressCreateRequest {
    @NotBlank(message = "progressText 不能为空")
    private String progressText;
    private Integer progressPercent;
}
