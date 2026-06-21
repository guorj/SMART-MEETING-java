package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarVchatOptionsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaults_enablesFeishuVc() {
        CalendarVchatOptions opts = CalendarVchatOptions.defaults();
        assertTrue(opts.enabled());
        assertEquals(CalendarVchatOptions.VC_TYPE_VC, opts.vcType());
        assertEquals("only_event_attendees", opts.joinMeetingPermission());
        assertTrue(opts.allowAttendeesStart());
        assertEquals(5, opts.reminderMinutes());
    }

    @Test
    void from_nullUsesDefaults() {
        CalendarVchatOptions opts = CalendarVchatOptions.from(null);
        assertTrue(opts.enabled());
        assertEquals(CalendarVchatOptions.VC_TYPE_VC, opts.vcType());
    }

    @Test
    void from_disabled() throws Exception {
        var node = objectMapper.readTree("{\"enabled\":false}");
        CalendarVchatOptions opts = CalendarVchatOptions.from(node);
        assertFalse(opts.enabled());
        assertEquals(CalendarVchatOptions.VC_TYPE_NO_MEETING, opts.vcType());
    }

    @Test
    void from_thirdParty() throws Exception {
        var node = objectMapper.readTree("""
                {"vcType":"third_party","meetingUrl":"https://oa.example.com/rec/abc"}
                """);
        CalendarVchatOptions opts = CalendarVchatOptions.from(node);
        assertTrue(opts.enabled());
        assertEquals(CalendarVchatOptions.VC_TYPE_THIRD_PARTY, opts.vcType());
        assertEquals("https://oa.example.com/rec/abc", opts.meetingUrl());
    }
}
