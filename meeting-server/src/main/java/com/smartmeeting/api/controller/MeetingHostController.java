package com.smartmeeting.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.host.MeetingHostSessionService;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/host/meetings")
@RequiredArgsConstructor
public class MeetingHostController {

    private final MeetingHostSessionService meetingHostSessionService;
    private final JwtUtil jwtUtil;

    @PostMapping("/{meetingId}/start")
    public ApiResponse<JsonNode> start(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) HostStartRequest body) {
        String token = bearer(authorization);
        jwtUtil.verifyRecordingPageToken(token, meetingId);
        meetingHostSessionService.start(meetingId, body);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/pause")
    public ApiResponse<JsonNode> pause(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        meetingHostSessionService.pause(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/resume")
    public ApiResponse<JsonNode> resume(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        meetingHostSessionService.resume(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/next-topic")
    public ApiResponse<JsonNode> nextTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        meetingHostSessionService.nextTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/skip-topic")
    public ApiResponse<JsonNode> skipTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        meetingHostSessionService.skipTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @GetMapping("/{meetingId}/state")
    public ApiResponse<JsonNode> state(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(401, "缺少 Authorization: Bearer 凭证");
        }
        return authorization.substring(7).trim();
    }
}
