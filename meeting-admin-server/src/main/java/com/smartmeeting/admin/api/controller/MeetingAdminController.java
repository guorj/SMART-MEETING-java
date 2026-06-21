package com.smartmeeting.admin.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.MeetingAdminDetailDto;
import com.smartmeeting.admin.api.dto.MeetingAdminLinksDto;
import com.smartmeeting.admin.api.dto.MeetingAdminScheduleRequest;
import com.smartmeeting.admin.api.dto.MeetingAdminSummaryDto;
import com.smartmeeting.admin.api.dto.MeetingAdminUpdateRequest;
import com.smartmeeting.admin.api.dto.MeetingBatchDeleteResultDto;
import com.smartmeeting.admin.service.BotBridgeService;
import com.smartmeeting.admin.service.MeetingAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

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
            @RequestParam(required = false) Integer presetTypeCode,
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false) String chatId,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTimeFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTimeTo) {
        return ApiResponse.ok(meetingAdminService.list(page, size, status, presetTypeCode,
                creatorId, chatId, company, scheduledTimeFrom, scheduledTimeTo));
    }

    @GetMapping("/{meetingId}")
    public ApiResponse<MeetingAdminDetailDto> detail(@PathVariable String meetingId) {
        return ApiResponse.ok(meetingAdminService.detail(meetingId));
    }

    @PutMapping("/{meetingId}")
    public ApiResponse<JsonNode> updateMeeting(
            @PathVariable String meetingId,
            @RequestBody MeetingAdminUpdateRequest request) {
        return ApiResponse.ok(meetingAdminService.updateMeeting(meetingId, request));
    }

    @PatchMapping("/{meetingId}/schedule")
    public ApiResponse<JsonNode> reschedule(
            @PathVariable String meetingId,
            @RequestBody MeetingAdminScheduleRequest request) {
        return ApiResponse.ok(meetingAdminService.rescheduleMeeting(meetingId, request));
    }

    @PostMapping("/{meetingId}/participants")
    public ApiResponse<JsonNode> addParticipant(
            @PathVariable String meetingId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(meetingAdminService.addParticipant(meetingId, body));
    }

    @PutMapping("/{meetingId}/participants/{participantId}")
    public ApiResponse<JsonNode> updateParticipant(
            @PathVariable String meetingId,
            @PathVariable String participantId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(meetingAdminService.updateParticipant(meetingId, participantId, body));
    }

    @DeleteMapping("/{meetingId}/participants/{participantId}")
    public ApiResponse<Void> deleteParticipant(
            @PathVariable String meetingId,
            @PathVariable String participantId) {
        meetingAdminService.deleteParticipant(meetingId, participantId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{meetingId}/links")
    public ApiResponse<MeetingAdminLinksDto> links(@PathVariable String meetingId) {
        return ApiResponse.ok(meetingAdminService.buildLinks(meetingId));
    }

    @PostMapping("/{meetingId}/force-end")
    public ApiResponse<Map<String, Boolean>> forceEnd(@PathVariable String meetingId) {
        boolean ok = meetingAdminService.forceEndMeeting(meetingId);
        return ApiResponse.ok(Map.of("success", ok));
    }

    @PostMapping("/batch-delete")
    public ApiResponse<MeetingBatchDeleteResultDto> batchDelete(
            @RequestBody Map<String, java.util.List<String>> body) {
        java.util.List<String> ids = body != null ? body.get("meetingIds") : null;
        return ApiResponse.ok(meetingAdminService.batchDeleteMeetings(ids));
    }

    @GetMapping("/{meetingId}/push-logs")
    public ApiResponse<JsonNode> pushLogs(
            @PathVariable String meetingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(botBridge.get("/api/logs", Map.of(
                "meetingId", meetingId,
                "page", String.valueOf(page),
                "size", String.valueOf(size))));
    }
}
