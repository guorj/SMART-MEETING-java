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

/**
 * 录音页与主持页入口 URL 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meetings}，为会议生成带 JWT 的 Web 页面链接并持久化到会议记录。
 *
 * @see JwtUtil
 * @see MeetingWebPageUrls
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class RecordingController {

    private final MeetingMapper meetingMapper;
    private final JwtUtil jwtUtil;
    private final MeetingWebPageUrls meetingWebPageUrls;

    /**
     * 获取录音页面 URL 与 JWT token。
     * <p>
     * {@code GET /api/v1/meetings/{id}/recording-url}
     *
     * @param id 会议 ID
     * @return 含 url、token、meetingId、meetingTitle、expiresIn 的 Map
     * @throws BusinessException 会议不存在时（404）
     */
    @GetMapping("/{id}/recording-url")
    public ApiResponse<Map<String, Object>> getRecordingUrl(@PathVariable String id) {
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }

        // 生成 JWT token（包含 meetingId）
        String token = jwtUtil.generateOperatorMeetingToken(id, JwtUtil.TYPE_RECORDING);

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
     * 获取 AI 会议主持页 URL 与 JWT（type=host，与录音页共用结束会议接口凭证族）。
     *
     * @param id 会议 ID
     * @return 含 url、token、meetingId、meetingTitle、expiresIn 的 Map
     * @throws BusinessException 会议不存在时（404）
     */
    @GetMapping("/{id}/host-url")
    public ApiResponse<Map<String, Object>> getHostUrl(@PathVariable String id) {
        Meeting meeting = meetingMapper.selectById(id);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + id);
        }
        String token = jwtUtil.generateOperatorMeetingToken(id, JwtUtil.TYPE_HOST);
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
