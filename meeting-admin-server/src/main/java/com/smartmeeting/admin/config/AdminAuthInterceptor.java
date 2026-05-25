package com.smartmeeting.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.admin.api.dto.ApiResponse;
import com.smartmeeting.admin.auth.AdminSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminProperties adminProperties;
    private final AdminSessionStore sessionStore;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!adminProperties.isEnabled()) {
            writeError(response, 503, "admin disabled");
            return false;
        }
        String expected = adminProperties.getToken();
        if (expected == null || expected.isBlank()) {
            writeError(response, 503, "admin token not configured");
            return false;
        }
        String token = request.getHeader("X-Admin-Token");
        if (expected.equals(token)) {
            return true;
        }
        if (sessionStore.isValid(token)) {
            return true;
        }
        writeError(response, 401, "invalid admin token");
        return false;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? 401 : 503);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
