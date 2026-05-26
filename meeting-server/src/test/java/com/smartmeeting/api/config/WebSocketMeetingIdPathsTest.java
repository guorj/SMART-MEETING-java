package com.smartmeeting.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebSocketMeetingIdPathsTest {

    @Test
    void resolvesMeetingIdWithContextPath() {
        assertEquals("m-abc",
                WebSocketMeetingIdPaths.meetingIdFromPath("/meeting-server/ws/host/m-abc"));
        assertEquals("m-xyz",
                WebSocketMeetingIdPaths.meetingIdFromPath("/meeting-server/ws/audio/m-xyz"));
    }

    @Test
    void resolvesMeetingIdWithoutContextPath() {
        assertEquals("m-abc", WebSocketMeetingIdPaths.meetingIdFromPath("/ws/host/m-abc"));
    }

    @Test
    void returnsNullForBlankPath() {
        assertNull(WebSocketMeetingIdPaths.meetingIdFromPath(null));
        assertNull(WebSocketMeetingIdPaths.meetingIdFromPath("   "));
    }
}
