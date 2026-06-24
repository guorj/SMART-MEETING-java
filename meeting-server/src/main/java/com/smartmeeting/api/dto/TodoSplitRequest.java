package com.smartmeeting.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办拆分请求体（{@code POST /api/v1/todos/{tid}/split}）。
 * <p>
 * 将一个父待办拆分为多个子待办；父待办状态不自动完成，由调用方后续维护。
 */
@Data
public class TodoSplitRequest {

    /** 子待办条目列表，至少 1 条 */
    @Valid
    @NotNull(message = "items 不能为空")
    @NotEmpty(message = "items 至少包含 1 条子待办")
    private List<Item> items;

    @Data
    public static class Item {
        /** 子待办内容 */
        @NotNull(message = "content 不能为空")
        private String content;
        /** 子待办责任人飞书 user_id；不传则继承父待办 */
        private String assigneeId;
        /** 子待办责任人姓名；不传则继承父待办 */
        private String assigneeName;
        /** 子待办经办人飞书 user_id；不传则继承父待办 */
        private String operatorId;
        /** 子待办经办人姓名；不传则继承父待办 */
        private String operatorName;
        /** 截止时间；不传则继承父待办 */
        private LocalDateTime deadline;
        /** 优先级；不传则继承父待办 */
        private String priority;
    }
}
