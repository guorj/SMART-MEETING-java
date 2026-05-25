package com.smartmeeting.admin.api.controller;

import com.smartmeeting.admin.config.FeishuOAuthProperties;
import com.smartmeeting.admin.service.FeishuOAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
public class AdminOAuthController {

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
                         HttpServletResponse response) throws IOException {
        String sessionToken = feishuOAuthService.handleCallback(code);
        response.sendRedirect("/admin?oauth_token=" + sessionToken);
    }
}
