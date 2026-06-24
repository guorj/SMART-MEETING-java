package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingTodoResponse;
import com.smartmeeting.api.dto.TodoAssignRequest;
import com.smartmeeting.api.dto.TodoSplitRequest;
import com.smartmeeting.api.dto.TodoStatusUpdateRequest;
import com.smartmeeting.service.DashboardGrantService;
import com.smartmeeting.service.TodoService;
import com.smartmeeting.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 待办项 REST 控制器（Dashboard token 鉴权）。
 */
@RestController
@RequestMapping("/api/v1/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final JwtUtil jwtUtil;
    private final DashboardGrantService dashboardGrantService;

  @GetMapping("/my")
  public ApiResponse<java.util.List<MeetingTodoResponse>> myTodos(@RequestParam("token") String token) {
    JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
    return ApiResponse.ok(todoService.listMyTodos(entry.feishuUserId()));
  }

    @GetMapping("/{tid}")
    public ApiResponse<MeetingTodoResponse> getTodo(
            @PathVariable String tid,
            @RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(todoService.getTodo(tid, entry.feishuUserId()));
    }

    @PutMapping("/{tid}/status")
    public ApiResponse<MeetingTodoResponse> updateStatus(
            @PathVariable String tid,
            @RequestParam("token") String token,
            @Valid @RequestBody TodoStatusUpdateRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(todoService.updateStatus(tid, request, entry.feishuUserId()));
    }

  @PutMapping("/{tid}/assign")
  public ApiResponse<MeetingTodoResponse> assign(
          @PathVariable String tid,
          @Valid @RequestBody TodoAssignRequest request) {
    return ApiResponse.ok(todoService.assign(tid, request));
  }

  /**
   * 拆分待办：将父待办拆分为多个子待办。
   */
  @PostMapping("/{tid}/split")
  public ApiResponse<java.util.List<MeetingTodoResponse>> split(
          @PathVariable String tid,
          @Valid @RequestBody TodoSplitRequest request) {
    return ApiResponse.ok(todoService.splitTodo(tid, request));
  }

    private JwtUtil.FeishuWebDashboardEntry parseDashboardEntry(String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return entry;
    }
}
