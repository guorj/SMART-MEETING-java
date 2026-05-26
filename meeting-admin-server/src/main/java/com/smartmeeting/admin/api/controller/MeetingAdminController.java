package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.MeetingAdminDetailDto;
import com.smartmeeting.admin.api.dto.MeetingAdminLinksDto;
import com.smartmeeting.admin.api.dto.MeetingAdminSummaryDto;
import com.smartmeeting.admin.service.MeetingAdminService;
import com.smartmeeting.admin.service.BotBridgeService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/meetings")
@RequiredArgsConstructor
public class MeetingAdminController {

    private final MeetingAdminService meetingAdminService;
    private final BotBridgeService botBridge;

    @GetMapping
    public ApiResponse<Page<MeetingAdminSummaryDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer presetTypeCode) {
        return ApiResponse.ok(meetingAdminService.list(page, size, status, presetTypeCode));
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingAdminDetailDto> detail(@PathVariable String meetingId) {
        return ApiResponse.ok(meetingAdminService.detail(meetingId));
    }

    @GetMapping("/{meetingId}/links")
    public ApiResponse<MeetingAdminLinksDto> links(@PathVariable String meetingId) {
        return ApiResponse.ok(meetingAdminService.buildLinks(meetingId));
    }

    @PostMapping("/{meetingId}/force-end")
    public ApiResponse<java.util.Map<String, Boolean>> forceEnd(@PathVariable String meetingId) {
        boolean ok = meetingAdminService.forceEndMeeting(meetingId);
        return ApiResponse.ok(java.util.Map.of("success", ok));
    }

    @GetMapping("/{meetingId}/push-logs")
    public ApiResponse<JsonNode> pushLogs(
            @PathVariable String meetingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(botBridge.get("/api/logs", java.util.Map.of(
                "meetingId", meetingId,
                "page", String.valueOf(page),
                "size", String.valueOf(size))));
    }
}
