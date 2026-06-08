package com.smartmeeting.asr;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XfyunOfflineClientQueryParamTest {

    @Test
    void generateDateTime_matchesXfyunPattern() {
        String raw = XfyunOfflineClient.generateDateTime();
        assertTrue(raw.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+\\d{4}"));
    }

    @Test
    void encodeQueryParam_encodesDateTimePlusOffset() {
        String raw = "2026-06-07T19:54:33+0800";
        assertEquals("2026-06-07T19%3A54%3A33%2B0800", XfyunOfflineClient.encodeQueryParam(raw));
    }

    @Test
    void buildEncodedQueryString_preservesPercentEncodedDateTime() {
        Map<String, String> params = new TreeMap<>();
        params.put("accessKeyId", "cf2d80e0d6bddb829c43d147dfc1d664");
        params.put("dateTime", "2026-06-07T20:29:05+0800");
        String query = XfyunOfflineClient.buildEncodedQueryString(params);
        assertTrue(query.contains("dateTime=2026-06-07T20%3A29%3A05%2B0800"));
        assertFalse(query.contains("+0800"));
    }

    @Test
    void encodeQueryParam_encodesChineseFileName() {
        assertEquals(
                "1%E5%88%86%E9%92%9F%E9%9F%B3%E9%A2%91.mp3",
                XfyunOfflineClient.encodeQueryParam("1分钟音频.mp3"));
    }
}
