package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.service.DashboardService;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final JwtUtil jwtUtil;

    @GetMapping("/me")
    public ApiResponse<DashboardService.UserInfo> me(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        return ApiResponse.ok(dashboardService.getUserInfo(entry.openId()));
    }

    @GetMapping("/voiceprint-status")
    public ApiResponse<DashboardService.VoiceprintStatusResult> voiceprintStatus(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        return ApiResponse.ok(dashboardService.getVoiceprintStatus(entry.openId()));
    }

    @GetMapping("/recent-meetings")
    public ApiResponse<List<DashboardService.MeetingSummary>> recentMeetings(
            @RequestParam("token") String token,
            @RequestParam(defaultValue = "10") int limit) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        int safeLimit = Math.max(1, Math.min(limit, 30));
        return ApiResponse.ok(dashboardService.getRecentMeetings(entry.openId(), safeLimit));
    }

    @GetMapping("/meeting-presets")
    public ApiResponse<List<MeetingPresetResponse>> meetingPresets(@RequestParam("token") String token) {
        jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        return ApiResponse.ok(dashboardService.getMeetingPresets());
    }

    @PostMapping("/create-meeting")
    public ApiResponse<MeetingResponse> createMeeting(
            @RequestParam("token") String token,
            @RequestBody MeetingCreateRequest request) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        return ApiResponse.ok(dashboardService.createMeeting(entry.openId(), entry.chatId(), request));
    }

    @GetMapping("/voiceprint-register-url")
    public ApiResponse<Map<String, String>> voiceprintRegisterUrl(@RequestParam("token") String token) {
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        String regToken = dashboardService.createVoiceprintSession(entry.openId(), entry.userName());
        String encoded = URLEncoder.encode(regToken, StandardCharsets.UTF_8);
        return ApiResponse.ok(Map.of("registerUrl", baseUrl + "/voiceprint?token=" + encoded));
    }
}