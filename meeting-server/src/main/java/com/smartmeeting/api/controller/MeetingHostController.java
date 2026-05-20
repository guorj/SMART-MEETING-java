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
 * 加时、点名等；所有写操作需 {@code type=host} 操作员 JWT，只读状态接口接受录音页 JWT。
 *
 * @see MeetingHostSessionService
 */
@RestController
@RequestMapping("/api/v1/host/meetings")
@RequiredArgsConstructor
public class MeetingHostController {

    private final MeetingHostSessionService meetingHostSessionService;
    private final JwtUtil jwtUtil;

    /**
     * 启动 AI 主持会话，可选覆盖会序与总时长。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @param body          可选启动参数（会序列表、总时长等）
     * @return 当前主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/start")
    public ApiResponse<JsonNode> start(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) HostStartRequest body) {
        String token = bearer(authorization);
        jwtUtil.verifyHostOperatorToken(token, meetingId);
        meetingHostSessionService.start(meetingId, body);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 暂停当前主持计时与会序推进。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/pause")
    public ApiResponse<JsonNode> pause(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.pause(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 恢复已暂停的主持会话。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/resume")
    public ApiResponse<JsonNode> resume(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.resume(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 进入下一议题。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/next-topic")
    public ApiResponse<JsonNode> nextTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.nextTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 跳过当前议题。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/skip-topic")
    public ApiResponse<JsonNode> skipTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.skipTopic(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 为当前议题延长预计时长。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @param body          加时分钟数，缺省 1
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/extend-topic")
    public ApiResponse<JsonNode> extendTopic(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody(required = false) HostExtendTopicRequest body) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        int minutes = (body != null && body.getMinutes() != null) ? body.getMinutes() : 1;
        meetingHostSessionService.extendTopicTime(meetingId, minutes);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 查询当前主持态（只读，录音页 JWT 可访问）。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 录音页或主持 JWT
     * @return 主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @GetMapping("/{meetingId}/state")
    public ApiResponse<JsonNode> state(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyRecordingPageToken(bearer(authorization), meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 开始点名环节。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/roll-call/start")
    public ApiResponse<JsonNode> rollCallStart(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.startRollCall(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 跳过当前点名对象。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 主持操作员 JWT
     * @return 更新后的主持态 JSON
     * @throws BusinessException 凭证无效时
     */
    @PostMapping("/{meetingId}/roll-call/skip-current")
    public ApiResponse<JsonNode> rollCallSkipCurrent(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        jwtUtil.verifyHostOperatorToken(bearer(authorization), meetingId);
        meetingHostSessionService.skipCurrentRollCall(meetingId);
        return ApiResponse.ok(meetingHostSessionService.getStateJson(meetingId));
    }

    /**
     * 从 {@code Authorization} 请求头解析 Bearer Token。
     *
     * @param authorization 请求头值
     * @return JWT 字符串
     * @throws BusinessException 缺少或格式不正确时
     */
    private static String bearer(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(401, "缺少 Authorization: Bearer 凭证");
        }
        return authorization.substring(7).trim();
    }
}
