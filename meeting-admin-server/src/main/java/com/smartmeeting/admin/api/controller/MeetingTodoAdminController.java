package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.MeetingTodoAttachmentDto;
import com.smartmeeting.admin.api.dto.MeetingTodoAuditDto;
import com.smartmeeting.admin.api.dto.MeetingTodoDto;
import com.smartmeeting.admin.api.dto.MeetingTodoProgressDto;
import com.smartmeeting.admin.api.dto.MeetingTodoSaveDto;
import com.smartmeeting.admin.auth.AdminAuthSupport;
import com.smartmeeting.admin.service.MeetingTodoAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/todos")
@RequiredArgsConstructor
public class MeetingTodoAdminController {

    private final MeetingTodoAdminService todoAdminService;

    @GetMapping
    public ApiResponse<Page<MeetingTodoDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String meetingId,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer presetTypeCode,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(todoAdminService.list(page, size, meetingId, assigneeId,
                status, presetTypeCode, keyword));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(todoAdminService.stats());
    }

    @GetMapping("/{id}")
    public ApiResponse<MeetingTodoDto> get(@PathVariable String id) {
        return ApiResponse.ok(todoAdminService.get(id));
    }

    @GetMapping("/{id}/audit")
    public ApiResponse<List<MeetingTodoAuditDto>> listAudit(@PathVariable String id) {
        return ApiResponse.ok(todoAdminService.listAudit(id));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<List<MeetingTodoProgressDto>> listProgress(@PathVariable String id) {
        return ApiResponse.ok(todoAdminService.listProgress(id));
    }

    @GetMapping("/{id}/attachments")
    public ApiResponse<List<MeetingTodoAttachmentDto>> listAttachments(@PathVariable String id) {
        return ApiResponse.ok(todoAdminService.listAttachments(id));
    }

    @PostMapping
    public ApiResponse<Map<String, String>> save(@RequestBody MeetingTodoSaveDto body) {
        return ApiResponse.ok(Map.of("id", todoAdminService.save(body)));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable String id,
                                          @RequestBody StatusUpdateReq body,
                                          HttpServletRequest request) {
        todoAdminService.forceUpdateStatus(
                id,
                body.getStatus(),
                body.getReason(),
                body.getCompletionNote(),
                body.getBlockReason(),
                AdminAuthSupport.operatorId(request),
                AdminAuthSupport.operatorName(request));
        return ApiResponse.ok();
    }

    @Data
    public static class StatusUpdateReq {
        private String status;
        private String reason;
        private String completionNote;
        private String blockReason;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @RequestBody(required = false) DeleteReq body,
                                    HttpServletRequest request) {
        String reason = body != null ? body.getReason() : null;
        todoAdminService.delete(id, reason,
                AdminAuthSupport.operatorId(request),
                AdminAuthSupport.operatorName(request));
        return ApiResponse.ok();
    }

    @Data
    public static class DeleteReq {
        private String reason;
    }
}
