package com.smartmeeting.util;

import com.smartmeeting.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 录音页 / 主持页 URL 共用 {@link JwtUtil#verifyRecordingPageToken(String, String)}。
 */
class JwtUtilRecordingPageTokenTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 4);
        ReflectionTestUtils.setField(jwtUtil, "feishuWebEntryExpireMinutes", 30);
    }

    @Test
    @DisplayName("subject + meetingId claim 与 path 一致则通过")
    void validToken_matchingMeetingId() {
        String mid = "m-100";
        String token = jwtUtil.generateToken(mid, Map.of("meetingId", mid));

        assertDoesNotThrow(() -> jwtUtil.verifyRecordingPageToken(token, mid));
    }

    @Test
    @DisplayName("type=host 的令牌可通过校验")
    void validHostType() {
        String mid = "m-host";
        String token = jwtUtil.generateToken(mid, Map.of("meetingId", mid, "type", "host"));

        assertDoesNotThrow(() -> jwtUtil.verifyRecordingPageToken(token, mid));
    }

    @Test
    @DisplayName("会议 ID 与 subject/claim 均不一致 → 403")
    void wrongMeetingId_throws403() {
        String token = jwtUtil.generateToken("other", Map.of("meetingId", "other"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> jwtUtil.verifyRecordingPageToken(token, "expected-id"));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("type 非 recording/host → 403")
    void invalidType_throws403() {
        String mid = "m-200";
        String token = jwtUtil.generateToken(mid, Map.of("meetingId", mid, "type", "admin"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> jwtUtil.verifyRecordingPageToken(token, mid));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("伪造或损坏的 JWT → 401")
    void garbageToken_throws401() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> jwtUtil.verifyRecordingPageToken("not.a.jwt", "m-1"));

        assertEquals(401, ex.getCode());
    }
}
