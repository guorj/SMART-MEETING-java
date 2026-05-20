package com.smartmeeting.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务健康检查控制器。
 * <p>
 * 基础路径 {@code /api/v1}，供负载均衡或运维探活使用。
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * 返回服务存活状态与时间戳。
     *
     * @return 含 code、message、timestamp 的 Map
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "code", 0,
                "message", "ok",
                "timestamp", System.currentTimeMillis()
        );
    }
}
