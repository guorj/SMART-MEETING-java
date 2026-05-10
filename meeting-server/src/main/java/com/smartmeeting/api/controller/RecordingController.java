package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class RecordingController {

    private final MeetingMapper meetingMapper;
    private final JwtUtil jwtUtil;

    @Value("${meeting.base-url:http://localhost:8765}")
    private String meetingBaseUrl;

    /**
     * 获取录音页面 URL + JWT token
     * GET /api/v1/meetings/{id}/recording-url
     */
    @GetMapping("/{id}/recording-url")
    public ApiResponse<Map<String, Object>> getRecordingUrl(@PathVariable String id) {
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }

        // 生成 JWT token（包含 meetingId）
        String token = jwtUtil.generateToken(id, Map.of("meetingId", id));

        // 与 FeishuCommandHandler 一致：使用 meeting.base-url，便于 frp/Nginx 公网访问
        String base = meetingBaseUrl.endsWith("/")
                ? meetingBaseUrl.substring(0, meetingBaseUrl.length() - 1)
                : meetingBaseUrl;
        String recordingUrl = base + "/rec/" + id + "?token=" + token;

        // 更新会议记录
        meeting.setRecordingUrl(recordingUrl);
        meeting.setRecordingToken(token);
        meetingMapper.updateById(meeting);

        return ApiResponse.ok(Map.of(
                "url", recordingUrl,
                "token", token,
                "meetingId", id,
                "meetingTitle", meeting.getTitle(),
                "expiresIn", 4 * 3600  // 4小时
        ));
    }
}
