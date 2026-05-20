package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.service.ParticipantLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 参会人链接 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meetings}，查询各参会人的个人入会链接等信息。
 *
 * @see ParticipantLinkService
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingParticipantController {

    private final ParticipantLinkService participantLinkService;

    /**
     * 列出参会人及个人入会链接（ONLINE 参会人含 joinUrl）。
     *
     * @param meetingId 会议 ID
     * @return 含 {@code meetingId} 与 {@code participants} 列表的 Map
     */
    @GetMapping("/{meetingId}/participant-links")
    public ApiResponse<Map<String, Object>> participantLinks(@PathVariable String meetingId) {
        List<MeetingResponse.ParticipantDTO> links = participantLinkService.buildParticipantLinks(meetingId);
        return ApiResponse.ok(Map.of("meetingId", meetingId, "participants", links));
    }
}
