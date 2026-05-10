package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.MeetingTodoResponse;
import com.smartmeeting.api.dto.PreviousProgressResponse;
import com.smartmeeting.api.dto.TodoBoardResponse;
import com.smartmeeting.service.FeishuMeetingStartCoordinator;
import com.smartmeeting.service.MeetingRecordingSessionEndService;
import com.smartmeeting.service.MeetingService;
import com.smartmeeting.service.TodoService;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final TodoService todoService;
    private final JwtUtil jwtUtil;
    private final FeishuMeetingStartCoordinator feishuMeetingStartCoordinator;
    private final MeetingRecordingSessionEndService meetingRecordingSessionEndService;

    @PostMapping
    public ApiResponse<MeetingResponse> createMeeting(@Valid @RequestBody MeetingCreateRequest request) {
        return ApiResponse.ok(meetingService.createMeeting(request));
    }

    /**
     * 飞书 Web 入口：请求头携带 {@code X-Feishu-Web-Entry-Token}（卡片链接中的 JWT），
     * 创建会议、启动并向对应飞书会话推送录音卡片（与飞书内文字指令效果一致）。
     */
    @PostMapping("/feishu-web/create-and-start")
    public ApiResponse<MeetingResponse> createAndStartFromFeishuWeb(
            @RequestHeader("X-Feishu-Web-Entry-Token") String webEntryToken,
            @Valid @RequestBody MeetingCreateRequest request) {
        JwtUtil.FeishuWebStartMeetingEntry entry = jwtUtil.parseAndVerifyFeishuWebStartMeetingEntry(webEntryToken.trim());
        MeetingResponse out = feishuMeetingStartCoordinator.createMeetingStartAndNotifyFeishu(
                entry.openId(), entry.chatId(), request);
        return ApiResponse.ok(out);
    }

    @PostMapping("/{id}/start")
    public ApiResponse<MeetingResponse> startMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.startMeeting(id));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<MeetingResponse> endMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.endMeeting(id));
    }

    /**
     * 录音页结束会议：须携带与页面 URL 相同的录音 JWT（{@code Authorization: Bearer …}）。
     * 已在录音时走 {@link RecordingService#stopRecording}，否则走 {@link MeetingService#endMeeting}，避免重复触发纪要链。
     */
    @PostMapping("/{id}/recording-session/end")
    public ApiResponse<MeetingResponse> endMeetingFromRecordingSession(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        String token = bearerToken(authorization);
        jwtUtil.verifyRecordingPageToken(token, id);
        return ApiResponse.ok(meetingRecordingSessionEndService.endFromRecordingPage(id));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new BusinessException(401, "缺少 Authorization: Bearer 录音凭证");
        }
        return authorization.substring(7).trim();
    }

    @GetMapping("/{id}")
    public ApiResponse<MeetingResponse> getMeeting(@PathVariable String id) {
        return ApiResponse.ok(meetingService.getMeeting(id));
    }

    @GetMapping
    public ApiResponse<List<MeetingResponse>> listMeetings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String creatorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(meetingService.listMeetings(status, creatorId, page, size));
    }

    /**
     * 查询上次会议待办进度（F-MID-02）
     * 用于会议开始时展示上次待办完成情况
     */
    @GetMapping("/{id}/previous-progress")
    public ApiResponse<PreviousProgressResponse> getPreviousProgress(@PathVariable String id) {
        return ApiResponse.ok(meetingService.getPreviousProgress(id));
    }

    @GetMapping("/{id}/todos")
    public ApiResponse<List<MeetingTodoResponse>> listTodos(@PathVariable String id) {
        return ApiResponse.ok(todoService.listTodosByMeeting(id));
    }

    @GetMapping("/{id}/todo-board")
    public ApiResponse<TodoBoardResponse> getTodoBoard(@PathVariable String id) {
        return ApiResponse.ok(todoService.getTodoBoard(id));
    }
}
