package com.smartmeeting.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.host.HostExtendTopicRequest;
import com.smartmeeting.api.dto.host.HostStartRequest;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.host.MeetingHostSessionService;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 会议主持 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/host/meetings}，供主持页操作会序推进、暂停/恢复、
 * 加时、点名等；所有写操作需 {@code type=host/recording} 操作员 JWT 且为主控本人，
 * 只读状态接口接受录音页/旁观 JWT。
 *
 * @see MeetingHostSessionService
 */
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
        JwtUtil.ParticipantMeetingToken pt = verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.start(meetingId, body, pt.feishuUserId(), pt.displayName());
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/pause")
    public ApiResponse<JsonNode> pause(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.pause(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/resume")
    public ApiResponse<JsonNode> resume(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.resume(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/next-topic")
    public ApiResponse<JsonNode> nextTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.nextTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/skip-topic")
    public ApiResponse<JsonNode> skipTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.skipTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/extend-topic")
    public ApiResponse<JsonNode> extendTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) HostExtendTopicRequest body) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        int minutes = (body != null && body.getMinutes() != null) ? body.getMinutes() : 1;
        meetingHostSessionService.extendTopicTime(meetingId, minutes);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @GetMapping("/{meetingId}/state")
    public ApiResponse<JsonNode> state(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/roll-call/start")
    public ApiResponse<JsonNode> rollCallStart(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.startRollCall(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    @PostMapping("/{meetingId}/roll-call/skip-current")
    public ApiResponse<JsonNode> rollCallSkipCurrent(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        verifyOperatorWrite(bearer(authorization), meetingId);
        meetingHostSessionService.skipCurrentRollCall(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    private JwtUtil.ParticipantMeetingToken verifyOperatorWrite(String token, String meetingId) {
        jwtUtil.verifyHostOperatorToken(token, meetingId);
        JwtUtil.ParticipantMeetingToken pt = jwtUtil.parseParticipantMeetingToken(token, meetingId);
        meetingHostSessionService.requireOperator(meetingId, pt.feishuUserId());
        return pt;
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(401, "缺少 Authorization: Bearer 凭证");
        }
        return authorization.substring(7).trim();
    }
}
