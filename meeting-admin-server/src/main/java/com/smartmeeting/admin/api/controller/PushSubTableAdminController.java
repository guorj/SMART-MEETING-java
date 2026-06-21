package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.service.PushSubTableAdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 推送任务/推送日志子表直连管理：多接收方、额外推送日、排除推送日、已读用户明细。
 * feishu-scheduled-bot 未对这些子表提供独立 REST API，admin 共享同一 intelligence 库直接读写。
 */
@RestController
@RequestMapping("/api/v1/admin/push-sub")
@RequiredArgsConstructor
public class PushSubTableAdminController {

    private final PushSubTableAdminService pushSubTableAdminService;

    @GetMapping("/logs/{pushLogId}/read-users")
    public ApiResponse<List<?>> listReadUsers(@PathVariable String pushLogId) {
        return ApiResponse.ok(pushSubTableAdminService.listReadUsers(pushLogId));
    }

    @GetMapping("/tasks/{taskId}/targets")
    public ApiResponse<List<?>> listTargets(@PathVariable String taskId) {
        return ApiResponse.ok(pushSubTableAdminService.listTargets(taskId));
    }

    @PostMapping("/tasks/{taskId}/targets")
    public ApiResponse<Map<String, String>> addTarget(@PathVariable String taskId, @RequestBody TargetReq body) {
        String id = pushSubTableAdminService.addTarget(taskId, body.getTargetType(), body.getTargetId(), body.getSortOrder());
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/targets/{targetRowId}")
    public ApiResponse<Void> removeTarget(@PathVariable String targetRowId) {
        pushSubTableAdminService.removeTarget(targetRowId);
        return ApiResponse.ok();
    }

    @GetMapping("/tasks/{taskId}/extra-dates")
    public ApiResponse<List<?>> listExtraDates(@PathVariable String taskId) {
        return ApiResponse.ok(pushSubTableAdminService.listExtraDates(taskId));
    }

    @PostMapping("/tasks/{taskId}/extra-dates")
    public ApiResponse<Map<String, String>> addExtraDate(@PathVariable String taskId,
                                                         @RequestBody @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) DateReq body) {
        String id = pushSubTableAdminService.addExtraDate(taskId, body.getDate());
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/extra-dates/{rowId}")
    public ApiResponse<Void> removeExtraDate(@PathVariable String rowId) {
        pushSubTableAdminService.removeExtraDate(rowId);
        return ApiResponse.ok();
    }

    @GetMapping("/tasks/{taskId}/exclude-dates")
    public ApiResponse<List<?>> listExcludeDates(@PathVariable String taskId) {
        return ApiResponse.ok(pushSubTableAdminService.listExcludeDates(taskId));
    }

    @PostMapping("/tasks/{taskId}/exclude-dates")
    public ApiResponse<Map<String, String>> addExcludeDate(@PathVariable String taskId,
                                                           @RequestBody DateReq body) {
        String id = pushSubTableAdminService.addExcludeDate(taskId, body.getDate());
        return ApiResponse.ok(Map.of("id", id));
    }

    @DeleteMapping("/exclude-dates/{rowId}")
    public ApiResponse<Void> removeExcludeDate(@PathVariable String rowId) {
        pushSubTableAdminService.removeExcludeDate(rowId);
        return ApiResponse.ok();
    }

    @Data
    public static class TargetReq {
        private String targetType;
        private String targetId;
        private Integer sortOrder;
    }

    @Data
    public static class DateReq {
        private LocalDate date;
    }
}
