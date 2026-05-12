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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 讯飞在线语音合成（流式 WebSocket v2/tts），单次会话合成一段文本。
 * 文档：https://www.xfyun.cn/doc/tts/online_tts/API.html
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

    @Value("${meeting.tts.vcn:xiaoyan}")
    private String vcn;

    @Value("${meeting.tts.connect-timeout-sec:15}")
    private int connectTimeoutSec;

    @Value("${meeting.tts.read-timeout-sec:60}")
    private int readTimeoutSec;

    /**
     * @return 16k raw PCM s16le 拼接；失败返回空数组
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
            return synthesizeBlocking(text);
        } catch (Exception e) {
            log.warn("Xfyun TTS failed: {}", e.getMessage());
            return new byte[0];
        }
    }

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

    private String buildRequestJson(String text) throws Exception {
        String textB64 = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("common").put("app_id", appId);
        ObjectNode business = root.putObject("business");
        business.put("aue", "raw");
        business.put("auf", "audio/L16;rate=16000");
        business.put("vcn", vcn);
        business.put("speed", 50);
        business.put("volume", 50);
        business.put("tte", "UTF8");
        ObjectNode data = root.putObject("data");
        data.put("status", 2);
        data.put("text", textB64);
        return objectMapper.writeValueAsString(root);
    }
}
