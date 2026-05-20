package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.service.MeetingTypePresetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 会务类型预设 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meeting-type-presets}，返回固定 1–5 类库表预设及虚拟第 6 项「其他会议」。
 *
 * @see MeetingTypePresetService
 * @see MeetingPresetResponse
 */
@RestController
@RequestMapping("/api/v1/meeting-type-presets")
@RequiredArgsConstructor
public class MeetingPresetController {

    private final MeetingTypePresetService meetingTypePresetService;

    /**
     * 固定 1-5 类会务预设 + 虚拟第 6 项「其他会议」（不入库，由前端/用户填主题）。
     *
     * @return 预设列表，最后一项 code=6 表示自定义主题会议
     */
    @GetMapping
    public ApiResponse<List<MeetingPresetResponse>> listPresets() {
        List<MeetingPresetResponse> list = new ArrayList<>(meetingTypePresetService.listPresets());
        list.add(MeetingPresetResponse.builder()
                .code(6)
                .displayName("其他会议（需填写会议主题）")
                .company(MeetingTypePresetService.DEFAULT_COMPANY)
                .groupName(MeetingTypePresetService.OTHER_GROUP)
                .scheduleNote(null)
                .agendaSummary("自定义主题与议程")
                .organizerName(null)
                .leaderName(null)
                .participantNames(List.of())
                .build());
        return ApiResponse.ok(list);
    }
}
