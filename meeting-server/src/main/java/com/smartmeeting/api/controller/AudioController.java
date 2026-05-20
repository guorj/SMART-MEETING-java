package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.service.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 录音控制 REST 控制器。
 * <p>
 * 基础路径 {@code /api/v1/audio}，提供会议录音的开始、暂停、恢复、停止及状态查询。
 * 浏览器实时推流场景主要走 WebSocket，本组接口供 REST 侧或管理工具调用。
 *
 * @see RecordingService
 */
@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
public class AudioController {

    private final RecordingService recordingService;

    /**
     * 开始录音。
     * <p>
     * {@code POST /api/v1/audio/{meetingId}/start}
     *
     * @param meetingId 会议 ID
     * @return 本地音频文件路径
     */
    @PostMapping("/{meetingId}/start")
    public ApiResponse<String> startRecording(@PathVariable String meetingId) {
        String audioPath = recordingService.startRecording(meetingId);
        return ApiResponse.ok(audioPath);
    }

    /**
     * 暂停录音。
     * <p>
     * {@code POST /api/v1/audio/{meetingId}/pause}
     *
     * @param meetingId 会议 ID
     * @return 空成功响应
     */
    @PostMapping("/{meetingId}/pause")
    public ApiResponse<Void> pauseRecording(@PathVariable String meetingId) {
        recordingService.pauseRecording(meetingId);
        return ApiResponse.ok();
    }

    /**
     * 继续录音。
     * <p>
     * {@code POST /api/v1/audio/{meetingId}/resume}
     *
     * @param meetingId 会议 ID
     * @return 恢复后的音频文件路径
     */
    @PostMapping("/{meetingId}/resume")
    public ApiResponse<String> resumeRecording(@PathVariable String meetingId) {
        String audioPath = recordingService.resumeRecording(meetingId);
        return ApiResponse.ok(audioPath);
    }

    /**
     * 停止录音并触发后续处理链。
     * <p>
     * {@code POST /api/v1/audio/{meetingId}/stop}
     *
     * @param meetingId 会议 ID
     * @return 停止结果（含时长等元数据）
     */
    @PostMapping("/{meetingId}/stop")
    public ApiResponse<Map<String, Object>> stopRecording(@PathVariable String meetingId) {
        Map<String, Object> result = recordingService.stopRecording(meetingId);
        return ApiResponse.ok(result);
    }

    /**
     * 获取当前录音状态。
     * <p>
     * {@code GET /api/v1/audio/{meetingId}/status}
     *
     * @param meetingId 会议 ID
     * @return 录音状态；未开始录音时返回 404 错误码
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
