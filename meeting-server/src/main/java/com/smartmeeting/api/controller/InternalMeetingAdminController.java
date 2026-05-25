package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.api.dto.internal.RefreshHostAgendaRequest;
import com.smartmeeting.api.dto.internal.RefreshHostAgendaResult;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.service.internal.InternalMeetingAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalMeetingAdminController {

    private final InternalApiAuth internalApiAuth;
    private final InternalMeetingAdminService internalMeetingAdminService;

    @PostMapping("/meetings/refresh-host-agenda")
    public ApiResponse<RefreshHostAgendaResult> refreshHostAgenda(
            @RequestBody RefreshHostAgendaRequest request,
            HttpServletRequest httpRequest) {
        internalApiAuth.requireToken(httpRequest);
        return ApiResponse.ok(internalMeetingAdminService.refreshHostAgenda(request));
    }

    @PostMapping("/presets/{code}/refresh-cache")
    public ApiResponse<Void> refreshPresetCache(@PathVariable int code, HttpServletRequest httpRequest) {
        internalApiAuth.requireToken(httpRequest);
        internalMeetingAdminService.refreshPresetCache(code);
        return ApiResponse.ok();
    }

    @PostMapping("/presets/refresh-cache-all")
    public ApiResponse<Void> refreshAllPresetCache(HttpServletRequest httpRequest) {
        internalApiAuth.requireToken(httpRequest);
        internalMeetingAdminService.refreshPresetCache(0);
        return ApiResponse.ok();
    }
}
