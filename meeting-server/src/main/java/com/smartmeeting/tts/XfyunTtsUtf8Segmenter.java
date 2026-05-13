package com.smartmeeting.tts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 讯飞在线 TTS 单次请求的文本长度受限（文档：单次 data.text 的 base64 长度须小于 8000 字节，约两千汉字），
 * 超长文本需按 UTF-8 安全边界切段后再多次合成。
 */
final class XfyunTtsUtf8Segmenter {

    private static final String SOFT_BREAK_CHARS = "。\n!?！？；;，,、\r";

    private XfyunTtsUtf8Segmenter() {
    }

    /**
     * @param maxUtf8Bytes 单段原文 UTF-8 字节上限（须小于接口限制并留余量）
     */
    static List<String> split(String text, int maxUtf8Bytes) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (maxUtf8Bytes < 256) {
            maxUtf8Bytes = 256;
        }
        byte[] full = text.getBytes(StandardCharsets.UTF_8);
        if (full.length <= maxUtf8Bytes) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < full.length) {
            int rawEnd = Math.min(start + maxUtf8Bytes, full.length);
            rawEnd = alignUtf8End(full, start, rawEnd);
            if (rawEnd <= start) {
                rawEnd = Math.min(start + maxUtf8Bytes, full.length);
            }
            String window = new String(full, start, rawEnd - start, StandardCharsets.UTF_8);
            int cut = findSoftCut(window);
            if (cut > 0 && cut < window.length()) {
                String head = window.substring(0, cut);
                byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
                if (headBytes.length > 0) {
                    out.add(head);
                    start += headBytes.length;
                    continue;
                }
            }
            if (!window.isBlank()) {
                out.add(window);
            }
            start = rawEnd;
        }
        return out;
    }

    /** 避免在 UTF-8 多字节字符中间截断 */
    private static int alignUtf8End(byte[] full, int start, int end) {
        int e = end;
        while (e > start && e < full.length && (full[e] & 0xC0) == 0x80) {
            e--;
        }
        return e;
    }

    /** 在窗口后半段找句读，优先靠后的切分点 */
    private static int findSoftCut(String window) {
        int searchLow = Math.max(1, (int) (window.length() * 0.35));
        for (int i = window.length() - 1; i >= searchLow; i--) {
            if (SOFT_BREAK_CHARS.indexOf(window.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return window.length();
    }
}
