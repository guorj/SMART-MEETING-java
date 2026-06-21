package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.MeetingScheduleRequest;
import com.smartmeeting.api.dto.MeetingScheduleResponse;
import com.smartmeeting.service.DashboardGrantService;
import com.smartmeeting.service.DashboardService;
import com.smartmeeting.service.MeetingScheduleService;
import com.smartmeeting.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Dashboard API 控制器（统一 token 参数鉴权）。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    @Value("${meeting.base-url:http://localhost:8765}")
    private String baseUrl;

    private final DashboardService dashboardService;
    private final MeetingScheduleService meetingScheduleService;
    private final JwtUtil jwtUtil;
    private final DashboardGrantService dashboardGrantService;

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        DashboardService.UserInfo userInfo = dashboardService.getUserInfo(entry.feishuUserId(), entry.userName());
        DashboardGrantService.UserPermissions perms =
                dashboardGrantService.buildUserPermissions(entry.feishuUserId(), entry.userName());
        MeResponse me = new MeResponse(userInfo, perms);
        return ApiResponse.ok(me);
    }

    @GetMapping("/voiceprint-status")
    public ApiResponse<DashboardService.VoiceprintStatusResult> voiceprintStatus(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.getVoiceprintStatus(entry.feishuUserId()));
    }

    @GetMapping("/recent-meetings")
    public ApiResponse<List<DashboardService.MeetingSummary>> recentMeetings(
            @RequestParam("token") String token,
            @RequestParam(defaultValue = "10") int limit) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        int safeLimit = Math.max(1, Math.min(limit, 30));
        return ApiResponse.ok(dashboardService.getRecentMeetings(entry.feishuUserId(), safeLimit));
    }

    @GetMapping("/active-meeting")
    public ApiResponse<DashboardService.ActiveMeetingResult> activeMeeting(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.getActiveMeeting(entry.feishuUserId()));
    }

    @GetMapping("/draft-meetings")
    public ApiResponse<List<DashboardService.ActiveMeetingResult>> draftMeetings(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.getDraftMeetings(entry.feishuUserId()));
    }

    @PostMapping("/active-meeting/end")
    public ApiResponse<MeetingResponse> endActiveMeeting(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireEndMeeting(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.endActiveMeeting(entry.feishuUserId()));
    }

    @PostMapping("/active-meeting/recover")
    public ApiResponse<DashboardService.ActiveMeetingResult> recoverActiveMeeting(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireCreateMeeting(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.recoverActiveMeeting(entry.feishuUserId()));
    }

    @PostMapping("/meetings/{meetingId}/cancel-draft")
    public ApiResponse<MeetingResponse> cancelDraftMeeting(
            @RequestParam("token") String token,
            @PathVariable String meetingId) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireEndMeeting(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.cancelDraftMeeting(entry.feishuUserId(), meetingId));
    }

    @GetMapping("/meeting-presets")
    public ApiResponse<List<MeetingPresetResponse>> meetingPresets(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.getMeetingPresets());
    }

    @PostMapping("/create-meeting")
    public ApiResponse<MeetingResponse> createMeeting(
            @RequestParam("token") String token,
            @Valid @RequestBody MeetingCreateRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireCreateMeeting(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.createMeeting(entry.feishuUserId(), entry.chatId(), request));
    }

    @PostMapping("/schedule-meeting")
    public ApiResponse<MeetingResponse> scheduleMeeting(
            @RequestParam("token") String token,
            @Valid @RequestBody MeetingCreateRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireCreateMeeting(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.scheduleMeeting(entry.feishuUserId(), entry.chatId(), request));
    }

    @GetMapping("/scheduled-meetings")
    public ApiResponse<List<DashboardService.MeetingSummary>> scheduledMeetings(
            @RequestParam("token") String token,
            @RequestParam(defaultValue = "10") int limit) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireDashboardAccess(entry.feishuUserId());
        return ApiResponse.ok(dashboardService.getScheduledMeetings(entry.feishuUserId(), limit));
    }

    @PatchMapping("/meetings/{meetingId}/schedule")
    public ApiResponse<MeetingScheduleResponse> rescheduleMeeting(
            @RequestParam("token") String token,
            @PathVariable String meetingId,
            @RequestBody MeetingScheduleRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireCreateMeeting(entry.feishuUserId());
        return ApiResponse.ok(meetingScheduleService.reschedule(meetingId, request));
    }

    @GetMapping("/voiceprint-register-url")
    public ApiResponse<Map<String, String>> voiceprintRegisterUrl(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        dashboardGrantService.requireVoiceprintRegistration(entry.feishuUserId());
        String regToken = dashboardService.createVoiceprintSession(entry.feishuUserId(), entry.userName());
        String encoded = URLEncoder.encode(regToken, StandardCharsets.UTF_8);
        return ApiResponse.ok(Map.of("registerUrl", baseUrl + "/voiceprint?token=" + encoded));
    }

    /**
     * {@code /me} 响应体：用户信息 + 权限摘要。
     *
     * @param userInfo    用户基本信息（含 OA 映射、声纹状态等）
     * @param permissions 白名单权限摘要
     */
    public record MeResponse(
            DashboardService.UserInfo userInfo,
            DashboardGrantService.UserPermissions permissions
    ) {
    }
}