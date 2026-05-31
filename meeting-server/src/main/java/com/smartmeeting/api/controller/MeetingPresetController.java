package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.service.MeetingTypePresetService;
import com.smartmeeting.service.PresetAgendaDocService;
import com.smartmeeting.service.cache.PresetBundle;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会务类型预设 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/meeting-type-presets}，返回库表中的全部会议模板预设。
 *
 * @see MeetingTypePresetService
 * @see MeetingPresetResponse
 */
@RestController
@RequestMapping("/api/v1/meeting-type-presets")
@RequiredArgsConstructor
public class MeetingPresetController {

    private final MeetingTypePresetService meetingTypePresetService;
    private final PresetAgendaDocService presetAgendaDocService;

    /**
     * 返回库表中的全部会务模板预设（含临时会议模板）。
     */
    @GetMapping
    public ApiResponse<List<MeetingPresetResponse>> listPresets() {
        return ApiResponse.ok(meetingTypePresetService.listPresets());
    }

    /**
     * 运维：从 DB 强制刷新指定 preset 的 Redis 缓存（正整数 code）。
     */
    @PostMapping("/{code}/cache/refresh")
    public ApiResponse<String> refreshPresetCache(@PathVariable int code) {
        if (code <= 0) {
            throw new BusinessException(400, "仅支持正整数 preset code");
        }
        PresetBundle bundle = presetAgendaDocService.refreshPresetBundle(code);
        int docCount = bundle != null ? bundle.embeddedDocCount() : 0;
        return ApiResponse.ok("refreshed preset " + code + ", embedded docs=" + docCount);
    }
}
