package com.smartmeeting.tts;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 讯飞在线 TTS 长文本 UTF-8 安全切段工具。
 * <p>
 * 讯飞接口要求单次 {@code data.text} 的 Base64 长度小于 8000 字节；本类在 UTF-8 字节边界上切分，
 * 并优先在句读、标点处软断句，供 {@link XfyunOnlineTtsSynthesizeService} 分段多次合成后拼接 PCM。
 * </p>
 */
final class XfyunTtsUtf8Segmenter {

    /** 优先作为软切分点的字符集合 */
    private static final String SOFT_BREAK_CHARS = "。\n!?！？；;，,、\r";

    /** 工具类，禁止实例化 */
    private XfyunTtsUtf8Segmenter() {
    }

    /**
     * 将原文按 UTF-8 字节上限切分为多段，保证每段可独立 Base64 编码后满足讯飞长度限制。
     *
     * @param text         待合成全文；null 或空串返回空列表
     * @param maxUtf8Bytes 单段 UTF-8 字节上限，小于 256 时会被抬升至 256
     * @return 分段后的字符串列表；未超长时返回仅含原文的单元素列表
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

    /**
     * 将截断位置回退到 UTF-8 码点边界，避免切断多字节字符。
     *
     * @param full  完整 UTF-8 字节数组
     * @param start 本段起始下标
     * @param end   候选结束下标（开区间右端）
     * @return 对齐后的结束下标
     */
    private static int alignUtf8End(byte[] full, int start, int end) {
        int e = end;
        while (e > start && e < full.length && (full[e] & 0xC0) == 0x80) {
            e--;
        }
        return e;
    }

    /**
     * 在窗口后半段查找句读/标点，返回适合软切分的字符下标（开区间右端）。
     *
     * @param window 当前待切分窗口文本
     * @return 切分位置；无合适标点时返回 {@code window.length()}
     */
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
