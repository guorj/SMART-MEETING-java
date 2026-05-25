package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.api.dto.MeetingMinuteViewDto;
import com.smartmeeting.admin.api.dto.TranscriptLineDto;
import com.smartmeeting.admin.service.ObservabilityAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/observability")
@RequiredArgsConstructor
public class ObservabilityAdminController {

    private final ObservabilityAdminService observabilityAdminService;

    @GetMapping("/meetings/{meetingId}/minute")
    public ApiResponse<MeetingMinuteViewDto> minute(@PathVariable String meetingId) {
        return ApiResponse.ok(observabilityAdminService.getMinute(meetingId));
    }

    @GetMapping("/meetings/{meetingId}/transcripts")
    public ApiResponse<List<TranscriptLineDto>> transcripts(
            @PathVariable String meetingId,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "true") boolean finalsOnly) {
        return ApiResponse.ok(observabilityAdminService.listTranscripts(meetingId, limit, finalsOnly));
    }
}
