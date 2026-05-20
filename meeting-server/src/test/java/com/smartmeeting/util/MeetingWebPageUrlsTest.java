package com.smartmeeting.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MeetingWebPageUrls} 单元测试：验证录音页 URL 拼接与缓存破坏参数。
 */
class MeetingWebPageUrlsTest {

    private final MeetingWebPageUrls urls = new MeetingWebPageUrls();

    /** 注入测试用 baseUrl 并清空缓存破坏参数。 */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urls, "meetingBaseUrl", "https://example.com/");
        ReflectionTestUtils.setField(urls, "pageCacheBuster", "");
    }

    /** 无缓存破坏参数时应生成标准录音页 URL。 */
    @Test
    void recordingUrl_noBuster() {
        assertEquals(
                "https://example.com/rec/m1?token=tok",
                urls.recordingPageUrl("m1", "tok"));
    }

    /** 配置 pageCacheBuster 时 URL 应附加 v 参数。 */
    @Test
    void recordingUrl_withBuster() {
        ReflectionTestUtils.setField(urls, "pageCacheBuster", "build-9");
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("token=tok"));
        assertTrue(urls.recordingPageUrl("m1", "tok").contains("&v=build-9"));
    }
}
