package com.smartmeeting.api.controller;

import com.smartmeeting.api.dto.ApiResponse;
import com.smartmeeting.config.InternalApiAuth;
import com.smartmeeting.config.MeetingRuntimeConfigLoader;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class InternalRuntimeConfigController {

    private final InternalApiAuth internalApiAuth;
    private final MeetingRuntimeConfigLoader runtimeConfigLoader;

    @PostMapping("/runtime-config/reload")
    public ApiResponse<Void> reload(HttpServletRequest request) {
        internalApiAuth.requireToken(request);
        runtimeConfigLoader.reload();
        return ApiResponse.ok();
    }
}
