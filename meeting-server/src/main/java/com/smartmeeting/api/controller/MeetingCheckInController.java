package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.ParticipantCheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 参会人签到 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meetings}，供线上参会人通过个人入会链接 JWT 登记到场。
 *
 * @see ParticipantCheckInService
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingCheckInController {

    private final ParticipantCheckInService participantCheckInService;

    /**
     * 线上参会人打开个人入会链接后调用，登记到场（无需麦克风/推流）。
     *
     * @param meetingId     会议 ID
     * @param authorization {@code Bearer} 个人入会 JWT
     * @return 签到结果（含参会人状态等）
     * @throws BusinessException 凭证缺失、无效或与会议不匹配时
     */
    @PostMapping("/{meetingId}/check-in")
    public ApiResponse<Map<String, Object>> checkIn(
            @PathVariable String meetingId,
            @RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(participantCheckInService.checkInFromToken(meetingId, bearer(authorization)));
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
