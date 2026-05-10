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

@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PutMapping("/{tid}/status")
    public ApiResponse<MeetingTodoResponse> updateStatus(
            @PathVariable String tid,
            @Valid @RequestBody TodoStatusUpdateRequest request) {
        return ApiResponse.ok(todoService.updateStatus(tid, request));
    }

    @PutMapping("/{tid}/assign")
    public ApiResponse<MeetingTodoResponse> assign(
            @PathVariable String tid,
            @Valid @RequestBody TodoAssignRequest request) {
        return ApiResponse.ok(todoService.assign(tid, request));
    }
}
