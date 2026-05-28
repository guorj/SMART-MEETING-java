package com.smartmeeting.util;

import com.smartmeeting.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilFeishuDashboardTokenTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 4);
        ReflectionTestUtils.setField(jwtUtil, "feishuWebEntryExpireMinutes", 30);
        ReflectionTestUtils.setField(jwtUtil, "feishuWebDashboardExpireHours", 8);
    }

    @Test
    void shouldGenerateAndParseDashboardToken() {
        String token = jwtUtil.generateFeishuWebDashboardToken("116afd4c", "oc_def", "Alan");
        JwtUtil.FeishuWebDashboardEntry entry = jwtUtil.parseAndVerifyFeishuWebDashboardToken(token);
        assertEquals("116afd4c", entry.feishuUserId());
        assertEquals("oc_def", entry.chatId());
        assertEquals("Alan", entry.userName());
    }

    @Test
    void shouldRejectTokenWithWrongPurpose() {
        String token = jwtUtil.generateFeishuWebStartMeetingEntryToken("116afd4c", "oc_def");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> jwtUtil.parseAndVerifyFeishuWebDashboardToken(token));
        assertEquals(401, ex.getCode());
    }
}
