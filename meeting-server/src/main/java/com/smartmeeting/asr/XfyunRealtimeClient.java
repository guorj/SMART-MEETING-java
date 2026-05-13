package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.util.XfyunSignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 讯飞实时 ASR WebSocket 客户端
 * 
 * 协议:
 * - 握手: wss://...?authorization=...&date=...&host=...
 * - 帧格式: JSON + base64 音频
 * - 返回: 中间结果 + 最终结果
 */
@Slf4j
@Component
public class XfyunRealtimeClient {

    @Value("${meeting.asr.xfyun.app-id:test}")
    private String appId;

    @Value("${meeting.asr.xfyun.api-key:test}")
    private String apiKey;

    @Value("${meeting.asr.xfyun.api-secret:test}")
    private String apiSecret;

    @Value("${meeting.asr.xfyun.ws-url:wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1}")
    private String wsUrl;
    
    @Value("${meeting.asr.xfyun.feature-ids:}")
    private String featureIdsConfig;

    /** 握手成功后阻塞等待的毫秒数（0=不等待）；过大影响首字延迟，代码内上限 60000。 */
    @Value("${meeting.asr.xfyun.post-open-wait-ms:0}")
    private int postOpenWaitMs;

    private WebSocketClient client;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean firstFrame = new AtomicBoolean(true);
    private final List<Consumer<AsrResult>> callbacks = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String currentMeetingId;
    private String sessionId;  // 讯飞会话ID（参考官方Demo）

    /**
     * 连接讯飞实时 ASR（参考 Python 版 xfyun_client.py）
     */
    public synchronized boolean connect(String meetingId) {
        if (connected.get()) {
            log.warn("Already connected to Xfyun ASR");
            return false;
        }

        currentMeetingId = meetingId;
        firstFrame.set(true);

        try {
            // 解析 feature_ids 配置
            List<String> featureIds = null;
            if (featureIdsConfig != null && !featureIdsConfig.isEmpty()) {
                featureIds = Arrays.asList(featureIdsConfig.split(","));
            }
            
            // 生成鉴权 URL（Python 版算法：参数排序 → HmacSHA1 → Base64）
            String authUrl = XfyunSignatureUtil.buildOfficeApiAuthUrl(
                wsUrl, appId, apiKey, apiSecret, "autodialect", 2, featureIds);

            URI uri = new URI(authUrl);
            log.info("Connecting to Xfyun ASR: {}", maskUrl(uri.toString()));

            final int waitAfterOpenMs = Math.max(0, Math.min(postOpenWaitMs, 60_000));
            if (postOpenWaitMs != waitAfterOpenMs) {
                log.warn("post-open-wait-ms={} clamped to {}", postOpenWaitMs, waitAfterOpenMs);
            }

            CountDownLatch latch = new CountDownLatch(1);

            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("【ASR连接成功】status={}, postOpenWaitMs={}", handshake.getHttpStatus(), waitAfterOpenMs);
                    connected.set(true);
                    firstFrame.set(true);
                    if (waitAfterOpenMs > 0) {
                        try {
                            Thread.sleep(waitAfterOpenMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    log.info("【ASR就绪】开始接收消息");
                    latch.countDown();
                }

                @Override
                public void onMessage(String message) {
                    try {
                        handleResponse(message);
                    } catch (Exception e) {
                        log.error("Failed to parse ASR response", e);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("Xfyun ASR closed: code={}, reason={}", code, reason);
                    connected.set(false);
                    latch.countDown();  // Release the waiting thread
                }

                @Override
                public void onError(Exception ex) {
                    log.error("Xfyun ASR error: {}", ex.getMessage());
                    connected.set(false);
                    latch.countDown();
                }
            };

            // 异步连接，等待结果
            client.connect();
            boolean connectedInTime = latch.await(10, TimeUnit.SECONDS);

            if (!connectedInTime || !connected.get()) {
                log.error("Xfyun ASR connection timeout or failed");
                disconnect();
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Failed to connect to Xfyun ASR", e);
            disconnect();
            return false;
        }
    }

    /**
     * 发送音频帧（直接发送二进制 PCM，参考 Python 版）
     */
    public synchronized void sendAudio(byte[] pcmData) {
        if (!connected.get() || client == null || !client.isOpen()) {
            log.debug("Not connected, skipping audio send");
            return;
        }

        try {
            // Python 版：直接发送二进制 PCM 数据
            client.send(pcmData);
            firstFrame.set(false);

        } catch (Exception e) {
            log.error("Failed to send audio to Xfyun ASR", e);
        }
    }

    /**
     * 结束识别（参考 Python 版）
     */
    public synchronized void end() {
        if (client != null && client.isOpen()) {
            try {
                // Python 版：{"end": true, "sessionId": "xxx"}
                String endMsg = String.format("{\"end\": true, \"sessionId\": \"%s\"}", (sessionId != null ? sessionId : currentMeetingId));
                client.send(endMsg);
                log.info("Sent end message to Xfyun ASR");
            } catch (Exception e) {
                log.error("Failed to send end message", e);
            }
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        if (client != null) {
            try {
                if (client.isOpen()) {
                    client.close(1000, "Normal closure");
                }
            } catch (Exception e) {
                log.warn("Error closing Xfyun connection", e);
            }
            client = null;
        }
        connected.set(false);
        firstFrame.set(true);
        currentMeetingId = null;
    }

    /**
     * 注册转写结果回调
     */
    public void setTranscriptCallback(Consumer<AsrResult> callback) {
        callbacks.add(callback);
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getCurrentMeetingId() {
        return currentMeetingId;
    }

    /**
     * 处理讯飞返回的识别结果
     */
    /**
     * 处理讯飞返回的识别结果（参考 Python 版 + 官方Demo）
     */
    private void handleResponse(String message) throws Exception {
        // 打印所有收到的消息（INFO级别方便调试）
        log.info("【ASR消息】收到: {}", message.length() > 500 ? message.substring(0, 500) + "..." : message);
        
        JsonNode root = objectMapper.readTree(message);

        // 兼容两种响应格式：msg_type 或 action
        String msgType = root.path("msg_type").asText(root.path("action").asText(""));
        
        // 处理错误消息
        if (msgType.equals("error")) {
            int code = root.path("code").asInt(0);
            log.error("【ASR错误】code={}, message={}", code, root.path("message").asText());
            if (code == 35006) {
                log.error("【ASR错误】讯飞并发路数已满");
            }
            return;
        }
        
        // 处理 action 消息：获取 sessionId（参考官方Demo）
        if (msgType.equals("action")) {
            JsonNode data = root.path("data");
            if (data.has("sessionId")) {
                sessionId = data.path("sessionId").asText();
                log.info("【ASR会话】获取sessionId: {}", sessionId);
            }
            return;
        }

        // 处理转写结果
        if (!msgType.equals("result")) {
            log.debug("【ASR其他】msg_type={}", msgType);
            return;
        }

        JsonNode data = root.path("data");
        
        // 检查 ls 字段（是否最后一条结果）
        boolean isLast = data.path("ls").asBoolean(false);
        if (isLast) {
            log.info("【ASR结束】ls=true，最后一条结果");
        }
        
        JsonNode cn = data.path("cn");
        JsonNode st = cn.path("st");
        
        if (st.isMissingNode()) {
            log.warn("【ASR解析】缺少st字段");
            return;
        }

        // 解析识别结果（Python 版 _parse_result）
        int resultType = st.path("type").asInt(1);  // 0=final, 1=interim
        boolean isFinal = (resultType == 0);
        
        StringBuilder text = new StringBuilder();
        String speakerChanged = null;
        
        // 解析 rt.ws.cw 路径
        for (JsonNode rt : st.path("rt")) {
            for (JsonNode ws : rt.path("ws")) {
                for (JsonNode cw : ws.path("cw")) {
                    String word = cw.path("w").asText("");
                    String rl = cw.path("rl").asText(null);
                    if (rl != null && !rl.equals("0")) {
                        speakerChanged = rl;
                    }
                    text.append(word);
                }
            }
        }
        
        if (text.length() == 0) {
            log.debug("【ASR解析】文本为空");
            return;
        }

        AsrResult asrResult = new AsrResult();
        asrResult.setIsLast(isLast);  // 设置是否最后一条结果
        asrResult.setText(text.toString());
        asrResult.setConfidence(isFinal ? 1.0 : 0.5);
        asrResult.setFinalResult(isFinal);
        asrResult.setMeetingId(currentMeetingId);
        asrResult.setSpeakerId(speakerChanged != null ? speakerChanged : "unknown");
        
        int bg = st.path("bg").asInt(0);
        int ed = st.path("ed").asInt(0);
        asrResult.setStartTimeMs(bg);
        asrResult.setEndTimeMs(ed);
        
        log.info("【ASR转写】text={}, speaker={}, isFinal={}, time={}-{}", 
            text.toString().substring(0, Math.min(30, text.length())), speakerChanged, isFinal, isLast, bg, ed);
        
        for (Consumer<AsrResult> callback : callbacks) {
            callback.accept(asrResult);
        }
    }
    private String maskUrl(String url) {
        int idx = url.indexOf("?");
        if (idx > 0) {
            return url.substring(0, Math.min(idx + 30, url.length())) + "...";
        }
        return url;
    }
}
