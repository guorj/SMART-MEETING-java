package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.api.dto.MeetingScheduleRequest;
import com.smartmeeting.api.dto.MeetingScheduleResponse;
import com.smartmeeting.api.dto.MeetingUpdateRequest;
import com.smartmeeting.api.dto.ParticipantAdminCreateRequest;
import com.smartmeeting.api.dto.ParticipantAdminUpdateRequest;
import com.smartmeeting.service.MeetingAdminUpdateService;
import com.smartmeeting.service.MeetingScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内网/Admin 桥接：会议字段更新与改期（不经 Dashboard JWT）。
 */
@RestController
@RequestMapping("/api/v1/internal/meetings")
@RequiredArgsConstructor
public class InternalMeetingController {

    private final MeetingAdminUpdateService meetingAdminUpdateService;
    private final MeetingScheduleService meetingScheduleService;

    @PutMapping("/{meetingId}")
    public ApiResponse<MeetingResponse> updateMeeting(
            @PathVariable String meetingId,
            @RequestBody MeetingUpdateRequest request) {
        return ApiResponse.ok(meetingAdminUpdateService.updateMeeting(meetingId, request));
    }

    @PatchMapping("/{meetingId}/schedule")
    public ApiResponse<MeetingScheduleResponse> reschedule(
            @PathVariable String meetingId,
            @RequestBody MeetingScheduleRequest request) {
        return ApiResponse.ok(meetingScheduleService.reschedule(meetingId, request));
    }

    @PostMapping("/{meetingId}/participants")
    public ApiResponse<MeetingResponse.ParticipantDTO> addParticipant(
            @PathVariable String meetingId,
            @RequestBody ParticipantAdminCreateRequest request) {
        return ApiResponse.ok(meetingAdminUpdateService.addParticipant(meetingId, request));
    }

    @PutMapping("/{meetingId}/participants/{participantId}")
    public ApiResponse<MeetingResponse.ParticipantDTO> updateParticipant(
            @PathVariable String meetingId,
            @PathVariable String participantId,
            @RequestBody ParticipantAdminUpdateRequest request) {
        return ApiResponse.ok(meetingAdminUpdateService.updateParticipant(meetingId, participantId, request));
    }

    @DeleteMapping("/{meetingId}/participants/{participantId}")
    public ApiResponse<Void> deleteParticipant(
            @PathVariable String meetingId,
            @PathVariable String participantId) {
        meetingAdminUpdateService.deleteParticipant(meetingId, participantId);
        return ApiResponse.ok(null);
    }
}
