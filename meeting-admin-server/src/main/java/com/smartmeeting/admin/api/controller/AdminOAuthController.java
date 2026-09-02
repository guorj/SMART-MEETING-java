package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.config.FeishuOAuthProperties;
import com.smartmeeting.admin.service.FeishuOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class AdminOAuthController {

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    private final FeishuOAuthProperties oauthProperties;
    private final FeishuOAuthService feishuOAuthService;

    @GetMapping("/admin/oauth/feishu/start")
    public void start(HttpServletResponse response) throws IOException {
        if (!oauthProperties.isEnabled()) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }
        response.sendRedirect(feishuOAuthService.buildAuthorizeUrl());
    }

    @GetMapping("/admin/oauth/feishu/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String state,
                         HttpServletResponse response) throws IOException {
        if (state != null && state.startsWith("minutes-oauth")) {
            feishuOAuthService.handleMinutesOAuthCallback(code, response);
            return;
        }
        String sessionToken = feishuOAuthService.handleCallback(code);
        response.sendRedirect(contextPath + "/admin?oauth_token=" + sessionToken);
    }
}
