package com.smartmeeting.util;

import com.smartmeeting.config.MeetingWebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeetingWebPageUrlsTest {

    private MeetingWebProperties webProperties;
    private MeetingWebPageUrls urls;

    @BeforeEach
    void setUp() {
        webProperties = new MeetingWebProperties();
        webProperties.setPageCacheBuster("");
        urls = new MeetingWebPageUrls("https://example.com/", webProperties);
    }

    @Test
    void recordingUrl_noBuster() {
        assertEquals(
                "https://example.com/rec/m1?token=tok",
                urls.recordingPageUrl("m1", "tok"));
    }

    @Test
    void recordingUrl_withBuster() {
        webProperties.setPageCacheBuster("build-9");
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("token=tok"));
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("&v=build-9"));
    }
}
