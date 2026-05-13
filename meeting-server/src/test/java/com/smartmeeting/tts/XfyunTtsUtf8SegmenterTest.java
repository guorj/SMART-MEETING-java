package com.smartmeeting.tts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XfyunTtsUtf8SegmenterTest {

    @Test
    void shortTextSingleSegment() {
        List<String> p = XfyunTtsUtf8Segmenter.split("答到。", 5300);
        assertEquals(1, p.size());
        assertEquals("答到。", p.get(0));
    }

    @Test
    void longTextSplitsAndRejoins() {
        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length < 12000) {
            sb.append("这是一段用于测试语音分段的长度填充文字。");
        }
        String s = sb.toString();
        List<String> parts = XfyunTtsUtf8Segmenter.split(s, 5300);
        assertTrue(parts.size() >= 2, "expected multiple segments");
        String rejoined = String.join("", parts);
        assertEquals(s, rejoined);
    }

    @Test
    void prefersBreakAtPunctuation() {
        String unit = "一二三四五六七八九十。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            sb.append(unit);
        }
        String s = sb.toString();
        List<String> parts = XfyunTtsUtf8Segmenter.split(s, 5300);
        assertTrue(parts.size() >= 2);
        for (String p : parts) {
            assertTrue(p.getBytes(StandardCharsets.UTF_8).length <= 5300,
                    "segment utf8 length: " + p.getBytes(StandardCharsets.UTF_8).length);
        }
    }
}
