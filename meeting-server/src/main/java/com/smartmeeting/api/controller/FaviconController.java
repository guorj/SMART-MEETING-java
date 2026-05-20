package com.smartmeeting.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点图标占位控制器。
 * <p>
 * 浏览器默认请求 {@code /favicon.ico}，无映射时会触发 {@code NoResourceFoundException} 并打 WARN。
 */
@RestController
public class FaviconController {

    /**
     * 返回 204 No Content，避免无意义 404 日志。
     *
     * @return 空响应体
     */
    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
