package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.TodoAttachmentResponse;
import com.smartmeeting.api.dto.TodoProgressCreateRequest;
import com.smartmeeting.api.dto.TodoProgressResponse;
import com.smartmeeting.entity.MeetingTodoAttachment;
import com.smartmeeting.service.DashboardGrantService;
import com.smartmeeting.service.TodoAttachmentService;
import com.smartmeeting.service.TodoProgressService;
import com.smartmeeting.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/todos/{tid}")
@RequiredArgsConstructor
public class TodoProgressController {

    private final TodoProgressService progressService;
    private final TodoAttachmentService attachmentService;
    private final JwtUtil jwtUtil;
    private final DashboardGrantService dashboardGrantService;

    @GetMapping("/progress")
    public ApiResponse<List<TodoProgressResponse>> listProgress(
            @PathVariable String tid,
            @RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(progressService.listProgress(tid, entry.feishuUserId()));
    }

    @PostMapping("/progress")
    public ApiResponse<TodoProgressResponse> addProgress(
            @PathVariable String tid,
            @RequestParam("token") String token,
            @Valid @RequestBody TodoProgressCreateRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(progressService.addProgress(
                tid, request, entry.feishuUserId(), entry.userName()));
    }

    @GetMapping("/attachments")
    public ApiResponse<List<TodoAttachmentResponse>> listAttachments(
            @PathVariable String tid,
            @RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(attachmentService.listAttachments(tid, entry.feishuUserId()));
    }

    @PostMapping("/attachments")
    public ApiResponse<TodoAttachmentResponse> uploadAttachment(
            @PathVariable String tid,
            @RequestParam("token") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "progressId", required = false) String progressId) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        return ApiResponse.ok(attachmentService.upload(
                tid, file, progressId, entry.feishuUserId(), entry.userName()));
    }

    @GetMapping("/attachments/{aid}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable String tid,
            @PathVariable String aid,
            @RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = parseDashboardEntry(token);
        Resource resource = attachmentService.loadForDownload(tid, aid, entry.feishuUserId());
        MeetingTodoAttachment meta = attachmentService.requireAttachment(aid);
        String contentType = meta.getMimeType() != null ? meta.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getFileName().replace("\"", "") + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private JwtUtil.FeishuWebDashboardEntry parseDashboardEntry(String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return entry;
    }
}
