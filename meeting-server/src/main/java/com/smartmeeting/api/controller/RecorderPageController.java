package com.smartmeeting.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 录音页面控制器
 * GET /rec/{meetingId} → 返回录音页面
 * GET /rec/{meetingId}?token=xxx → 自动携带 token
 */
@Controller
public class RecorderPageController {

    @GetMapping("/rec/{meetingId}")
    public String recorderPage(@PathVariable String meetingId) {
        // 转发到静态录音页面
        return "forward:/static/index.html";
    }
}
