package com.smartmeeting.api.controller;

import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Dashboard H5 页面入口。
 */
@Controller
@RequiredArgsConstructor
public class DashboardPageController {

    private final JwtUtil jwtUtil;

    @GetMapping("/dashboard")
    public ResponseEntity<Resource> dashboard(@RequestParam("token") String token) {
        jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate().mustRevalidate().noStore())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(new ClassPathResource("static/dashboard.html"));
    }
}
