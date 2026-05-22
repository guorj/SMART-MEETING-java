package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenClaw Gateway WebSocket RPC 客户端（{@code chat.send}）。
 *
 * <p>Gateway 控制面为 WebSocket JSON 协议，不存在 {@code POST /api/v1/sessions/send} HTTP 路由。
 */
@Slf4j
@Component
public class OpenClawGatewayWsClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 向指定会话发送消息并等待助手最终回复文本。
     *
     * @return 助手 Markdown/文本；失败返回 {@code null}
     */
    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds) {
        if (gatewayHttpUrl == null || gatewayHttpUrl.isBlank()
                || sessionKey == null || sessionKey.isBlank()
                || message == null || message.isBlank()) {
            return null;
        }
        boolean hasToken = authToken != null && !authToken.isBlank();
        boolean hasDeviceToken = deviceToken != null && !deviceToken.isBlank();
        if (!hasToken && !hasDeviceToken) {
            return null;
        }

        String wsUrl = toWebSocketUrl(gatewayHttpUrl);
        boolean loopback = isLoopbackGateway(gatewayHttpUrl);
        long deadlineMs = System.currentTimeMillis() + timeoutSeconds * 1000L;

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> assistantText = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        String connectId = "connect-" + UUID.randomUUID();
        String sendId = "send-" + UUID.randomUUID();

        try {
            WebSocketClient client = new WebSocketClient(URI.create(wsUrl)) {
                private volatile boolean handshakeDone;
                private volatile boolean chatSendAccepted;

                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.debug("OpenClaw WS connected: {}", wsUrl);
                }

                @Override
                public void onMessage(String frame) {
                    try {
                        JsonNode json = objectMapper.readTree(frame);
                        String type = json.path("type").asText("");

                        if ("event".equals(type)) {
                            String event = json.path("event").asText("");
                            if ("connect.challenge".equals(event) && !handshakeDone) {
                                sendConnect(connectId, authToken, deviceToken, loopback);
                                return;
                            }
                            if ("chat".equals(event)) {
                                JsonNode payload = json.path("payload");
                                captureChatAssistant(payload, assistantText);
                                if (chatSendAccepted && isChatStreamComplete(payload)) {
                                    done.countDown();
                                }
                            }
                            return;
                        }

                        if ("res".equals(type)) {
                            String id = json.path("id").asText("");
                            boolean ok = json.path("ok").asBoolean(false);
                            if (connectId.equals(id)) {
                                if (!ok) {
                                    errorRef.set("connect failed: " + formatGatewayError(json));
                                    log.warn("OpenClaw connect rejected: {}", errorRef.get());
                                    done.countDown();
                                    return;
                                }
                                handshakeDone = true;
                                JsonNode auth = json.path("payload").path("auth");
                                JsonNode scopes = auth.path("scopes");
                                log.info("OpenClaw connected: loopback={}, role={}, scopes={}",
                                        loopback, auth.path("role").asText(""), scopes);
                                if (!scopes.isArray() || scopes.isEmpty()) {
                                    errorRef.set("connect ok but scopes empty; use http://127.0.0.1:18789 "
                                            + "on the Gateway host, or set openclaw.device-token after pairing");
                                    done.countDown();
                                    return;
                                }
                                sendChat(sendId, sessionKey, message);
                                return;
                            }
                            if (sendId.equals(id)) {
                                if (!ok) {
                                    errorRef.set("chat.send failed: " + formatGatewayError(json));
                                    log.warn("OpenClaw chat.send rejected: {}", errorRef.get());
                                    done.countDown();
                                    return;
                                }
                                chatSendAccepted = true;
                                extractReplyFromPayload(json.path("payload"), assistantText);
                                if (assistantText.get() != null && !assistantText.get().isBlank()) {
                                    done.countDown();
                                }
                            }
                        }
                    } catch (Exception e) {
                        errorRef.set("parse frame: " + e.getMessage());
                        done.countDown();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (assistantText.get() == null && errorRef.get() == null) {
                        errorRef.set("ws closed: " + code + " " + reason);
                    }
                    done.countDown();
                }

                @Override
                public void onError(Exception ex) {
                    errorRef.set("ws error: " + ex.getMessage());
                    done.countDown();
                }

                private void sendConnect(String id, String token, String deviceToken, boolean loopback) {
                    ObjectNode params = objectMapper.createObjectNode();
                    params.put("minProtocol", 3);
                    params.put("maxProtocol", 4);
                    ObjectNode clientNode = params.putObject("client");
                    if (loopback) {
                        clientNode.put("id", "gateway-client");
                        clientNode.put("mode", "backend");
                    } else {
                        clientNode.put("id", "cli");
                        clientNode.put("mode", "cli");
                    }
                    clientNode.put("version", "0.1.0");
                    clientNode.put("platform", "java");
                    params.put("role", "operator");
                    params.putArray("scopes")
                            .add("operator.read")
                            .add("operator.write");
                    ObjectNode auth = params.putObject("auth");
                    if (deviceToken != null && !deviceToken.isBlank()) {
                        auth.put("deviceToken", deviceToken);
                    } else {
                        auth.put("token", token);
                    }
                    params.put("locale", "zh-CN");
                    params.put("userAgent", "smart-meeting-java/0.1.0");
                    sendRequest(id, "connect", params);
                }

                /**
                 * Gateway 新版 {@code chat.send} 要求 {@code sessionKey} + {@code idempotencyKey}（不再使用 {@code key}）。
                 */
                private void sendChat(String reqId, String sessionKey, String msg) {
                    ObjectNode params = objectMapper.createObjectNode();
                    params.put("sessionKey", sessionKey);
                    params.put("idempotencyKey", reqId);
                    params.put("message", msg);
                    sendRequest(reqId, "chat.send", params);
                }

                private void sendRequest(String id, String method, ObjectNode params) {
                    ObjectNode req = objectMapper.createObjectNode();
                    req.put("type", "req");
                    req.put("id", id);
                    req.put("method", method);
                    req.set("params", params);
                    send(req.toString());
                }
            };

            if (!client.connectBlocking(15, TimeUnit.SECONDS)) {
                log.warn("OpenClaw WS connect timeout: {}", wsUrl);
                return null;
            }

            long waitMs = Math.max(1000, deadlineMs - System.currentTimeMillis());
            if (!done.await(waitMs, TimeUnit.MILLISECONDS)) {
                log.warn("OpenClaw WS chat.send timeout after {}s sessionKey={}", timeoutSeconds, sessionKey);
                client.close();
                return assistantText.get();
            }

            if (errorRef.get() != null) {
                log.warn("OpenClaw WS error: {}", errorRef.get());
                return null;
            }

            return assistantText.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenClaw WS interrupted");
            return null;
        } catch (Exception e) {
            log.error("OpenClaw WS call failed: {}", e.getMessage());
            return null;
        }
    }

    /** HTTP(S) Gateway 根地址是否可达（{@code GET /health}）。 */
    public boolean pingHttp(String gatewayHttpUrl) {
        if (gatewayHttpUrl == null || gatewayHttpUrl.isBlank()) {
            return false;
        }
        try {
            String base = gatewayHttpUrl.replaceAll("/$", "");
            java.net.URL url = URI.create(base + "/health").toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code != 200) {
                return false;
            }
            byte[] bytes = conn.getInputStream().readAllBytes();
            String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return body.contains("ok");
        } catch (Exception e) {
            log.debug("OpenClaw health ping failed: {}", e.getMessage());
            return false;
        }
    }

    static boolean isLoopbackGateway(String gatewayHttpUrl) {
        try {
            URI uri = URI.create(toWebSocketUrl(gatewayHttpUrl));
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return "127.0.0.1".equals(host)
                    || "localhost".equalsIgnoreCase(host)
                    || "::1".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    static String toWebSocketUrl(String gatewayHttpUrl) {
        String u = gatewayHttpUrl.trim().replaceAll("/$", "");
        if (u.startsWith("https://")) {
            return "wss://" + u.substring(8);
        }
        if (u.startsWith("http://")) {
            return "ws://" + u.substring(7);
        }
        if (u.startsWith("ws://") || u.startsWith("wss://")) {
            return u;
        }
        return "ws://" + u;
    }

    private static void captureChatAssistant(JsonNode payload, AtomicReference<String> out) {
        if (payload == null || payload.isMissingNode()) {
            return;
        }
        if (payload.has("deltaText") && !payload.get("deltaText").isNull()) {
            String delta = payload.get("deltaText").asText("");
            if (!delta.isBlank()) {
                String prev = out.get();
                out.set(prev == null ? delta : prev + delta);
            }
            return;
        }
        String extracted = OpenClawReplyExtractor.extractFromJson(payload);
        if (extracted != null && !extracted.isBlank()) {
            out.set(extracted);
        }
    }

    private static String formatGatewayError(JsonNode res) {
        JsonNode err = res.path("error");
        if (err.isMissingNode() || err.isNull()) {
            return res.toString();
        }
        if (err.isTextual()) {
            return err.asText();
        }
        return err.toString();
    }

    /** Gateway 流式回复结束时 payload 常带 {@code done=true} 或 {@code state=final}。 */
    private static boolean isChatStreamComplete(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return false;
        }
        if (payload.path("done").asBoolean(false)) {
            return true;
        }
        String state = payload.path("state").asText("");
        return "final".equalsIgnoreCase(state) || "completed".equalsIgnoreCase(state);
    }

    private static void extractReplyFromPayload(JsonNode payload, AtomicReference<String> out) {
        captureChatAssistant(payload, out);
    }
}
