package com.smartmeeting.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.util.XfyunSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 讯飞在线语音合成（WebSocket v2/tts）服务：短连接、阻塞收齐 PCM。
 * <p>
 * 对外入口 {@link #synthesizeToPcm(String)}：超长文本由 {@link XfyunTtsUtf8Segmenter} 切段后多次合成拼接。
 * 输出 16 kHz、s16le 裸 PCM，供 {@link com.smartmeeting.service.host.MeetingHostSessionService} 等分片播放。
 * 鉴权 URL 由 {@link com.smartmeeting.util.XfyunSignatureUtil#assembleAuthUrl} 生成，与 ASR 共用
 * {@code meeting.asr.xfyun} 的 app-id / api-key / api-secret；发音人见 {@code meeting.tts.vcn}。
 * </p>
 * <p>
 * 文档：<a href="https://www.xfyun.cn/doc/tts/online_tts/API.html">在线语音合成 API</a>。
 * </p>
 */
@Slf4j
@Service
public class XfyunOnlineTtsSynthesizeService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${meeting.tts.ws-url:wss://tts-api.xfyun.cn/v2/tts}")
    private String ttsWsUrl;

    @Value("${meeting.asr.xfyun.app-id:test}")
    private String appId;

    @Value("${meeting.asr.xfyun.api-key:test}")
    private String apiKey;

    @Value("${meeting.asr.xfyun.api-secret:test}")
    private String apiSecret;

    @Value("${meeting.tts.vcn:x4_yezi}")
    private String vcn;

    @Value("${meeting.tts.connect-timeout-sec:15}")
    private int connectTimeoutSec;

    @Value("${meeting.tts.read-timeout-sec:120}")
    private int readTimeoutSec;

    /** 单段原文 UTF-8 字节上限（讯飞单次 text 的 base64 须小于 8000 字节，见文档） */
    @Value("${meeting.tts.max-text-utf8-bytes:5300}")
    private int maxTextUtf8Bytes;

    /**
     * 合成整段文本为 PCM：超长时按 UTF-8 与句读切段后多次调用讯飞再拼接。
     *
     * @param text 待朗读全文；null 或空白返回空数组
     * @return 16 kHz、s16le 裸 PCM 字节拼接；占位鉴权、失败或空白输入时返回空数组（非 null）
     */
    public byte[] synthesizeToPcm(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        if ("test".equals(apiKey) || "test".equals(apiSecret)) {
            log.debug("Skip Xfyun TTS: placeholder api credentials");
            return new byte[0];
        }
        try {
            List<String> parts = XfyunTtsUtf8Segmenter.split(text.strip(), maxTextUtf8Bytes);
            if (parts.isEmpty()) {
                return new byte[0];
            }
            if (parts.size() > 1) {
                log.info("Xfyun TTS: splitting into {} segment(s), maxUtf8Bytes={}", parts.size(), maxTextUtf8Bytes);
            }
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                byte[] seg = synthesizeBlocking(part);
                all.write(seg);
            }
            return all.toByteArray();
        } catch (Exception e) {
            log.warn("Xfyun TTS failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * 阻塞式拉完一整段文本的 PCM：建 WS、发单帧 text、收齐 audio 直至 status=2 或异常。
     * 与 {@link #synthesizeToPcm} 不同，本方法只处理已切段后的一段。
     *
     * @param text 单段不超过 {@link #maxTextUtf8Bytes} 约束的原文（由上层切段保证）
     * @return 单段 PCM 字节；连接失败、业务错误码且无音频时为 empty
     * @throws Exception 建连、解析等异常向上抛出，由 {@link #synthesizeToPcm} 捕获
     */
    private byte[] synthesizeBlocking(String text) throws Exception {
        String ws = XfyunSignatureUtil.assembleAuthUrl(ttsWsUrl, apiKey, apiSecret);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger codeHolder = new AtomicInteger(0);

        WebSocketClient client = new WebSocketClient(URI.create(ws)) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                try {
                    String payload = buildRequestJson(text);
                    send(payload);
                } catch (Exception e) {
                    log.error("TTS send request failed", e);
                    close();
                }
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonNode root = objectMapper.readTree(message);
                    int code = root.path("code").asInt(0);
                    codeHolder.set(code);
                    if (code != 0) {
                        log.warn("TTS response code={} msg={}", code, root.path("message").asText());
                        close();
                        return;
                    }
                    JsonNode data = root.path("data");
                    if (data.isMissingNode() || data.isNull()) {
                        return;
                    }
                    if (data.hasNonNull("audio")) {
                        byte[] chunk = Base64.getDecoder().decode(data.get("audio").asText());
                        pcm.write(chunk);
                    }
                    int status = data.path("status").asInt(0);
                    if (status == 2) {
                        close();
                    }
                } catch (Exception e) {
                    log.warn("TTS parse message: {}", e.getMessage());
                    close();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                done.countDown();
            }

            @Override
            public void onError(Exception ex) {
                log.warn("TTS websocket error: {}", ex.getMessage());
                done.countDown();
            }
        };

        if (!client.connectBlocking(connectTimeoutSec, TimeUnit.SECONDS)) {
            log.warn("TTS websocket connect timeout");
            return new byte[0];
        }
        if (!done.await(readTimeoutSec, TimeUnit.SECONDS)) {
            log.warn("TTS websocket read timeout");
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        if (codeHolder.get() != 0 && pcm.size() == 0) {
            return new byte[0];
        }
        return pcm.toByteArray();
    }

    /**
     * 组装讯飞 v2 TTS 首帧 JSON：business 指定 raw PCM、采样率、发音人及语速音量。
     *
     * @param text 原文（已在上层 strip）
     * @return JSON 字符串，作为 WebSocket 首条业务帧发送
     * @throws Exception JSON 序列化等异常
     */
    private String buildRequestJson(String text) throws Exception {
        String textB64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("common").put("app_id", appId);
        ObjectNode business = root.putObject("business");
        business.put("aue", "raw");
        business.put("auf", "audio/L16;rate=16000");
        business.put("vcn", vcn);
        // 讯飞文档：语速/音量常用 0～100，50 为默认档
        business.put("speed", 50);
        business.put("volume", 50);
        business.put("tte", "UTF8");
        ObjectNode data = root.putObject("data");
        data.put("status", 2);
        data.put("text", textB64);
        return objectMapper.writeValueAsString(root);
    }
}
