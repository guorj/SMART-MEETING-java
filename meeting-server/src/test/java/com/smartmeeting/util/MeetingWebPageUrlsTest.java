package com.smartmeeting.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MeetingWebPageUrlsTest {

    private final MeetingWebPageUrls urls = new MeetingWebPageUrls();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urls, "meetingBaseUrl", "https://example.com/");
        ReflectionTestUtils.setField(urls, "pageCacheBuster", "");
    }

    @Test
    void recordingUrl_noBuster() {
        assertEquals(
                "https://example.com/rec/m1?token=tok",
                urls.recordingPageUrl("m1", "tok"));
    }

    @Test
    void recordingUrl_withBuster() {
        ReflectionTestUtils.setField(urls, "pageCacheBuster", "build-9");
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("token=tok"));
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("&v=build-9"));
    }
}
