package com.smartmeeting.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeishuCommandRouterTest {

    private final FeishuCommandRouter router = new FeishuCommandRouter();

    @Test
    void shouldRouteDashboardCommand() {
        FeishuCommandRouter.CommandResult result = router.parse("会议管理");
        assertEquals("open_dashboard", result.getCommand());
    }

    @Test
    void shouldNotRouteLegacyStartCommand() {
        FeishuCommandRouter.CommandResult result = router.parse("开始会议 1");
        assertEquals("unknown", result.getCommand());
    }
}
