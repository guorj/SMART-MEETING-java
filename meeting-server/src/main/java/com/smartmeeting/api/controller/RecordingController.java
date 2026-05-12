package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.util.JwtUtil;
import com.smartmeeting.util.MeetingWebPageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class RecordingController {

    private final MeetingMapper meetingMapper;
    private final JwtUtil jwtUtil;
    private final MeetingWebPageUrls meetingWebPageUrls;

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

        String recordingUrl = meetingWebPageUrls.recordingPageUrl(id, token);

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

    /**
     * AI 会议主持页 URL + JWT（type=host，与录音页共用结束会议接口凭证族）
     */
    @GetMapping("/{id}/host-url")
    public ApiResponse<Map<String, Object>> getHostUrl(@PathVariable String id) {
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }
        String token = jwtUtil.generateToken(id, Map.of("meetingId", id, "type", "host"));
        String hostUrl = meetingWebPageUrls.hostPageUrl(id, token);
        return ApiResponse.ok(Map.of(
                "url", hostUrl,
                "token", token,
                "meetingId", id,
                "meetingTitle", meeting.getTitle(),
                "expiresIn", 4 * 3600
        ));
    }
}
