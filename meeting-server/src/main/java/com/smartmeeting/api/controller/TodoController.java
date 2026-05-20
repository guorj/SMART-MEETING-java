package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingTodoResponse;
import com.smartmeeting.api.dto.TodoAssignRequest;
import com.smartmeeting.api.dto.TodoStatusUpdateRequest;
import com.smartmeeting.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办项 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/todos}，提供单条待办的状态更新与责任人指派。
 *
 * @see TodoService
 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    /**
     * 更新待办状态（含完成说明、卡点原因等）。
     *
     * @param tid     待办 ID
     * @param request 状态及可选备注
     * @return 更新后的待办详情
     */
    @PutMapping("/{tid}/status")
    public ApiResponse<MeetingTodoResponse> updateStatus(
            @PathVariable String tid,
            @Valid @RequestBody TodoStatusUpdateRequest request) {
        return ApiResponse.ok(todoService.updateStatus(tid, request));
    }

    /**
     * 指派或变更待办责任人。
     *
     * @param tid     待办 ID
     * @param request 新责任人 ID 与可选姓名
     * @return 更新后的待办详情
     */
    @PutMapping("/{tid}/assign")
    public ApiResponse<MeetingTodoResponse> assign(
            @PathVariable String tid,
            @Valid @RequestBody TodoAssignRequest request) {
        return ApiResponse.ok(todoService.assign(tid, request));
    }
}
