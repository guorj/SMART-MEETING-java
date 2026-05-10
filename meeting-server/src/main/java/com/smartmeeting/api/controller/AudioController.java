package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.service.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
public class AudioController {

    private final RecordingService recordingService;

    /**
     * 开始录音
     * POST /api/v1/audio/{meetingId}/start
     */
    @PostMapping("/{meetingId}/start")
    public ApiResponse<String> startRecording(@PathVariable String meetingId) {
        String audioPath = recordingService.startRecording(meetingId);
        return ApiResponse.ok(audioPath);
    }

    /**
     * 暂停录音
     * POST /api/v1/audio/{meetingId}/pause
     */
    @PostMapping("/{meetingId}/pause")
    public ApiResponse<Void> pauseRecording(@PathVariable String meetingId) {
        recordingService.pauseRecording(meetingId);
        return ApiResponse.ok();
    }

    /**
     * 继续录音
     * POST /api/v1/audio/{meetingId}/resume
     */
    @PostMapping("/{meetingId}/resume")
    public ApiResponse<String> resumeRecording(@PathVariable String meetingId) {
        String audioPath = recordingService.resumeRecording(meetingId);
        return ApiResponse.ok(audioPath);
    }

    /**
     * 停止录音
     * POST /api/v1/audio/{meetingId}/stop
     */
    @PostMapping("/{meetingId}/stop")
    public ApiResponse<Map<String, Object>> stopRecording(@PathVariable String meetingId) {
        Map<String, Object> result = recordingService.stopRecording(meetingId);
        return ApiResponse.ok(result);
    }

    /**
     * 获取录音状态
     * GET /api/v1/audio/{meetingId}/status
     */
    @GetMapping("/{meetingId}/status")
    public ApiResponse<RecordingService.RecordingState> getRecordingStatus(@PathVariable String meetingId) {
        RecordingService.RecordingState state = recordingService.getRecordingState(meetingId);
        if (state == null) {
            return ApiResponse.error(404, "会议未开始录音");
        }
        return ApiResponse.ok(state);
    }
}
