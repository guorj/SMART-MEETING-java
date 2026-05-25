package com.smartmeeting.config;

import com.smartmeeting.api.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class InternalApiAuth {

    private final InternalReloadProperties internalProperties;

    public void requireToken(HttpServletRequest request) {
        if (!internalProperties.isReloadEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "internal api disabled");
        }
        String expected = internalProperties.getReloadToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "internal token not configured");
        }
        if (!expected.equals(request.getHeader("X-Internal-Token"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid internal token");
        }
    }
}
