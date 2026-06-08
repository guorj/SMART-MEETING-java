package com.smartmeeting.matterprogress.openclaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenClaw Gateway WebSocket RPC 客户端（{@code chat.send}）。
 *
 * <p>Gateway 控制面为 WebSocket JSON 协议，不存在 {@code POST /api/v1/sessions/send} HTTP 路由。
 *
 * <p>{@code chat.send} 通常仅返回 {@code {runId, status: started}}，正文通过 {@code chat} 事件流式下发，
 * 或在连接提前关闭时通过 {@code chat.history} 从 session transcript 补全（对齐 feishu-router openclaw-client.js）。
 *
 * <p>纯 POJO，不依赖 Spring；由消费方自行装配为 Bean 或直接实例化。
 */
public class OpenClawGatewayWsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenClawGatewayWsClient.class);

    private static final int HISTORY_FETCH_ATTEMPTS = 24;
    private static final int HISTORY_FETCH_DELAY_MS = 250;
    private static final int HISTORY_FETCH_EXTENDED_ATTEMPTS = 16;
    private static final int HISTORY_FETCH_EXTENDED_DELAY_MS = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds) {
        return sendChatMessage(gatewayHttpUrl, authToken, deviceToken, sessionKey, message, timeoutSeconds, null);
    }

    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds,
                                  String taskId) {
        return sendChatMessage(gatewayHttpUrl, authToken, deviceToken, sessionKey, message, timeoutSeconds, taskId, null);
    }

    /**
     * @param completionMarker 非空时，仅当回复正文包含该标记才提前结束（如 weekly-comparison 的 {@code generatedReportUrl=}）；
     *                         否则等到 {@code chat} 终态事件或超时。
     */
    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds,
                                  String taskId,
                                  String completionMarker) {
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
        AtomicReference<String> historyText = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicBoolean chatSendAccepted = new AtomicBoolean(false);
        AtomicBoolean chatTerminal = new AtomicBoolean(false);
        AtomicReference<String> chatRunId = new AtomicReference<>();
        AtomicInteger baselineAssistantCount = new AtomicInteger(0);
        AtomicReference<String> baselineAssistantText = new AtomicReference<>();
        AtomicInteger historyPollCount = new AtomicInteger(0);
        ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingReqs = new ConcurrentHashMap<>();
        String completionNeedle = completionMarker != null && !completionMarker.isBlank()
                ? completionMarker.trim()
                : null;

        String connectId = "connect-" + UUID.randomUUID();
        String wsRequestId = "send-" + UUID.randomUUID();
        String idempotencyKey = taskId != null && !taskId.isBlank()
                ? taskId.trim()
                : wsRequestId;
        final long t0 = System.currentTimeMillis();
        final long[] tWsConnected = {0L};
        final long[] tHandshakeOk = {0L};
        final long[] tChatSendAccepted = {0L};
        final long[] tFirstDelta = {0L};

        WebSocketClient client = null;
        try {
            captureSessionBaseline(gatewayHttpUrl, authToken, deviceToken, sessionKey, loopback,
                    baselineAssistantCount, baselineAssistantText);

            client = new WebSocketClient(URI.create(wsUrl)) {
                private volatile boolean handshakeDone;

                @Override
                public void onOpen(ServerHandshake handshake) {
                    tWsConnected[0] = System.currentTimeMillis();
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
                                String runId = chatRunId.get();
                                if (runId != null && payload.has("runId")
                                        && !runId.equals(payload.get("runId").asText(""))) {
                                    return;
                                }
                                if (tFirstDelta[0] == 0L && payload.has("deltaText")
                                        && !payload.get("deltaText").isNull()
                                        && !payload.get("deltaText").asText("").isBlank()) {
                                    tFirstDelta[0] = System.currentTimeMillis();
                                }
                                captureChatAssistant(payload, assistantText);
                                if (chatSendAccepted.get() && isChatTerminal(payload)) {
                                    chatTerminal.set(true);
                                    if (completionNeedle == null
                                            || shouldFinish(assistantText, historyText,
                                            baselineAssistantText.get(), completionNeedle)) {
                                        done.countDown();
                                    }
                                }
                            }
                            return;
                        }

                        if ("res".equals(type)) {
                            String id = json.path("id").asText("");
                            CompletableFuture<JsonNode> pending = pendingReqs.remove(id);
                            if (pending != null) {
                                if (json.path("ok").asBoolean(false)) {
                                    pending.complete(json.path("payload"));
                                } else {
                                    pending.completeExceptionally(
                                            new IllegalStateException(formatGatewayError(json)));
                                }
                                return;
                            }

                            boolean ok = json.path("ok").asBoolean(false);
                            if (connectId.equals(id)) {
                                if (!ok) {
                                    errorRef.set("connect failed: " + formatGatewayError(json));
                                    log.warn("OpenClaw connect rejected: {}", errorRef.get());
                                    done.countDown();
                                    return;
                                }
                                handshakeDone = true;
                                tHandshakeOk[0] = System.currentTimeMillis();
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
                                sendChat(wsRequestId, idempotencyKey, sessionKey, message);
                                return;
                            }
                            if (wsRequestId.equals(id)) {
                                if (!ok) {
                                    errorRef.set("chat.send failed: " + formatGatewayError(json));
                                    log.warn("OpenClaw chat.send rejected: {}", errorRef.get());
                                    done.countDown();
                                    return;
                                }
                                boolean cached = json.path("meta").path("cached").asBoolean(false);
                                String status = json.path("payload").path("status").asText("");
                                if (cached) {
                                    log.warn("OpenClaw chat.send deduplicated by Gateway: idempotencyKey={} status={} "
                                                    + "(Agent 不会重新执行；请使用每次唯一的 runKey)",
                                            idempotencyKey, status.isBlank() ? "cached" : status);
                                } else {
                                    log.info("OpenClaw chat.send accepted: idempotencyKey={} runId={} status={}",
                                            idempotencyKey,
                                            json.path("payload").path("runId").asText(""),
                                            status.isBlank() ? "started" : status);
                                }
                                chatSendAccepted.set(true);
                                tChatSendAccepted[0] = System.currentTimeMillis();
                                String runId = json.path("payload").path("runId").asText("");
                                chatRunId.set(runId.isBlank() ? idempotencyKey : runId);
                                if (chatTerminal.get() || isChatTerminal(json.path("payload"))) {
                                    chatTerminal.set(true);
                                    if (completionNeedle == null
                                            || shouldFinish(assistantText, historyText,
                                            baselineAssistantText.get(), completionNeedle)) {
                                        done.countDown();
                                    }
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
                    if (!chatSendAccepted.get() || chatTerminal.get()) {
                        if (assistantText.get() == null && errorRef.get() == null) {
                            errorRef.set("ws closed: " + code + " " + reason);
                        }
                        done.countDown();
                        return;
                    }
                    log.warn("OpenClaw WS closed early after chat.send (code={} reason={}); "
                                    + "will poll chat.history until timeout",
                            code, reason);
                }

                @Override
                public void onError(Exception ex) {
                    errorRef.set("ws error: " + ex.getMessage());
                    done.countDown();
                }

                private void sendConnect(String id, String token, String devToken, boolean lp) {
                    ObjectNode params = objectMapper.createObjectNode();
                    params.put("minProtocol", 3);
                    params.put("maxProtocol", 4);
                    ObjectNode clientNode = params.putObject("client");
                    if (lp) {
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
                    ObjectNode authNode = params.putObject("auth");
                    if (devToken != null && !devToken.isBlank()) {
                        authNode.put("deviceToken", devToken);
                    } else {
                        authNode.put("token", token);
                    }
                    params.put("locale", "zh-CN");
                    params.put("userAgent", "smart-meeting-java/0.1.0");
                    sendRequest(id, "connect", params);
                }

                private void sendChat(String reqId, String idemKey, String sKey, String msg) {
                    ObjectNode params = objectMapper.createObjectNode();
                    params.put("sessionKey", sKey);
                    params.put("idempotencyKey", idemKey);
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
                log.warn("OpenClaw WS connect timeout: {} elapsedMs={}", wsUrl, System.currentTimeMillis() - t0);
                return null;
            }
            if (tWsConnected[0] == 0L) {
                tWsConnected[0] = System.currentTimeMillis();
            }

            WebSocketClient activeClient = client;
            while (System.currentTimeMillis() < deadlineMs) {
                long remainMs = deadlineMs - System.currentTimeMillis();
                if (remainMs <= 0) {
                    break;
                }
                long sliceMs = Math.min(HISTORY_FETCH_DELAY_MS, remainMs);
                if (done.await(sliceMs, TimeUnit.MILLISECONDS)) {
                    break;
                }
                if (chatSendAccepted.get() && errorRef.get() == null) {
                    String polled = pollHistoryOnce(gatewayHttpUrl, authToken, deviceToken, sessionKey,
                            baselineAssistantCount.get(), loopback);
                    if (polled != null && !polled.isBlank()) {
                        historyText.set(polled);
                        historyPollCount.incrementAndGet();
                        if (shouldFinish(assistantText, historyText, baselineAssistantText.get(), completionNeedle)) {
                            done.countDown();
                            break;
                        }
                    }
                }
            }

            if (chatSendAccepted.get() && !shouldFinish(assistantText, historyText,
                    baselineAssistantText.get(), completionNeedle)) {
                int floor = baselineAssistantCount.get();
                fetchHistoryWithRetries(gatewayHttpUrl, authToken, deviceToken, sessionKey,
                        floor, historyText, loopback, false);
                if (!shouldFinish(assistantText, historyText, baselineAssistantText.get(), completionNeedle)) {
                    fetchHistoryWithRetries(gatewayHttpUrl, authToken, deviceToken, sessionKey,
                            floor, historyText, loopback, true);
                }
            }

            try {
                activeClient.close();
            } catch (Exception ignored) {
                /* ignore */
            }

            String reply = pickReplyText(assistantText.get(), historyText.get(), baselineAssistantText.get());
            long tComplete = System.currentTimeMillis();
            int replyLen = reply != null ? reply.length() : 0;
            log.info("OpenClaw WS timings idempotencyKey={} sessionKey={} connectMs={} handshakeMs={} chatSendMs={} "
                            + "firstDeltaMs={} totalMs={} replyLength={} chatTerminal={} historyPolls={} "
                            + "completionMarker={}",
                    idempotencyKey,
                    sessionKey,
                    deltaMs(tWsConnected[0], t0),
                    deltaMs(tHandshakeOk[0], tWsConnected[0]),
                    deltaMs(tChatSendAccepted[0], tHandshakeOk[0]),
                    deltaMs(tFirstDelta[0], tChatSendAccepted[0]),
                    tComplete - t0,
                    replyLen,
                    chatTerminal.get(),
                    historyPollCount.get(),
                    completionNeedle != null);

            if (reply != null && completionNeedle != null && !reply.contains(completionNeedle)) {
                log.warn("OpenClaw WS finished without completion marker idempotencyKey={} replyPreview={}",
                        idempotencyKey, previewReply(reply));
            } else if (reply != null && replyLen < 400) {
                log.warn("OpenClaw WS short reply idempotencyKey={} replyPreview={}",
                        idempotencyKey, previewReply(reply));
            }

            if (reply == null || reply.isBlank()) {
                if (errorRef.get() != null) {
                    log.warn("OpenClaw WS error: {}", errorRef.get());
                }
                return null;
            }
            return reply;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenClaw WS interrupted");
            return null;
        } catch (Exception e) {
            log.error("OpenClaw WS call failed: {}", e.getMessage());
            return null;
        }
    }

    private String pollHistoryOnce(String gatewayHttpUrl,
                                   String authToken,
                                   String deviceToken,
                                   String sessionKey,
                                   int baselineAssistantCount,
                                   boolean loopback) {
        try {
            JsonNode payload = fetchChatHistorySnapshot(gatewayHttpUrl, authToken, deviceToken,
                    sessionKey, loopback, 8000);
            return OpenClawChatHistory.extractNewAssistantText(payload.path("messages"), baselineAssistantCount);
        } catch (Exception e) {
            log.debug("OpenClaw chat.history poll: {}", formatException(e));
            return null;
        }
    }

    private void fetchHistoryWithRetries(String gatewayHttpUrl,
                                         String authToken,
                                         String deviceToken,
                                         String sessionKey,
                                         int baselineAssistantCount,
                                         AtomicReference<String> historyText,
                                         boolean loopback,
                                         boolean extended) {
        int attempts = extended ? HISTORY_FETCH_EXTENDED_ATTEMPTS : HISTORY_FETCH_ATTEMPTS;
        int delayMs = extended ? HISTORY_FETCH_EXTENDED_DELAY_MS : HISTORY_FETCH_DELAY_MS;
        for (int i = 0; i < attempts; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            try {
                JsonNode payload = fetchChatHistorySnapshot(gatewayHttpUrl, authToken, deviceToken,
                        sessionKey, loopback, 8000);
                String newText = OpenClawChatHistory.extractNewAssistantText(
                        payload.path("messages"), baselineAssistantCount);
                if (newText != null && !newText.isBlank()) {
                    historyText.set(newText);
                    return;
                }
            } catch (Exception e) {
                log.warn("OpenClaw chat.history fetch: {}", formatException(e));
            }
        }
    }

    private void captureSessionBaseline(String gatewayHttpUrl,
                                        String authToken,
                                        String deviceToken,
                                        String sessionKey,
                                        boolean loopback,
                                        AtomicInteger baselineCount,
                                        AtomicReference<String> baselineText) {
        try {
            JsonNode payload = fetchChatHistorySnapshot(gatewayHttpUrl, authToken, deviceToken,
                    sessionKey, loopback, 8000);
            JsonNode messages = payload.path("messages");
            int count = OpenClawChatHistory.countAssistantMessages(messages);
            baselineCount.set(count);
            baselineText.set(OpenClawChatHistory.extractLatestAssistantText(messages));
            log.debug("OpenClaw session baseline: sessionKey={} assistantCount={}", sessionKey, count);
        } catch (Exception e) {
            log.warn("OpenClaw baseline chat.history failed: sessionKey={} err={}",
                    sessionKey, formatException(e));
        }
    }

    private JsonNode fetchChatHistorySnapshot(String gatewayHttpUrl,
                                              String authToken,
                                              String deviceToken,
                                              String sessionKey,
                                              boolean loopback,
                                              int timeoutMs) throws Exception {
        String wsUrl = toWebSocketUrl(gatewayHttpUrl);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<JsonNode> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingReqs = new ConcurrentHashMap<>();
        String connectId = "connect-" + UUID.randomUUID();
        String historyId = "history-" + UUID.randomUUID();

        WebSocketClient historyClient = new WebSocketClient(URI.create(wsUrl)) {
            private volatile boolean handshakeDone;

            @Override
            public void onOpen(ServerHandshake handshake) {
                /* wait for connect.challenge */
            }

            @Override
            public void onMessage(String frame) {
                try {
                    JsonNode json = objectMapper.readTree(frame);
                    String type = json.path("type").asText("");
                    if ("event".equals(type)) {
                        if ("connect.challenge".equals(json.path("event").asText("")) && !handshakeDone) {
                            sendHistoryConnect(connectId, authToken, deviceToken, loopback);
                        }
                        return;
                    }
                    if (!"res".equals(type)) {
                        return;
                    }
                    String id = json.path("id").asText("");
                    CompletableFuture<JsonNode> pending = pendingReqs.remove(id);
                    if (pending != null) {
                        if (json.path("ok").asBoolean(false)) {
                            pending.complete(json.path("payload"));
                        } else {
                            pending.completeExceptionally(
                                    new IllegalStateException(formatGatewayError(json)));
                        }
                        return;
                    }
                    if (!connectId.equals(id)) {
                        return;
                    }
                    if (!json.path("ok").asBoolean(false)) {
                        error.set(new IllegalStateException("connect failed: " + formatGatewayError(json)));
                        ready.countDown();
                        return;
                    }
                    handshakeDone = true;
                    CompletableFuture<JsonNode> historyFuture = new CompletableFuture<>();
                    pendingReqs.put(historyId, historyFuture);
                    historyFuture.whenComplete((payload, ex) -> {
                        if (ex != null) {
                            error.set(ex instanceof Exception ? (Exception) ex
                                    : new IllegalStateException(String.valueOf(ex)));
                        } else {
                            result.set(payload);
                        }
                        ready.countDown();
                    });
                    sendHistoryRequest(historyId, "chat.history", historyParams(sessionKey));
                    return;
                } catch (Exception e) {
                    error.set(e);
                    ready.countDown();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (result.get() == null && error.get() == null) {
                    error.set(new IllegalStateException("ws closed: " + code + " " + reason));
                }
                ready.countDown();
            }

            @Override
            public void onError(Exception ex) {
                error.set(ex);
                ready.countDown();
            }

            private void sendHistoryConnect(String id, String token, String devToken, boolean lp) {
                ObjectNode params = objectMapper.createObjectNode();
                params.put("minProtocol", 3);
                params.put("maxProtocol", 4);
                ObjectNode clientNode = params.putObject("client");
                if (lp) {
                    clientNode.put("id", "gateway-client");
                    clientNode.put("mode", "backend");
                } else {
                    clientNode.put("id", "cli");
                    clientNode.put("mode", "cli");
                }
                clientNode.put("version", "0.1.0");
                clientNode.put("platform", "java");
                params.put("role", "operator");
                params.putArray("scopes").add("operator.read").add("operator.write");
                ObjectNode authNode = params.putObject("auth");
                if (devToken != null && !devToken.isBlank()) {
                    authNode.put("deviceToken", devToken);
                } else {
                    authNode.put("token", token);
                }
                params.put("locale", "zh-CN");
                params.put("userAgent", "smart-meeting-java/0.1.0");
                sendHistoryRequest(id, "connect", params);
            }

            private void sendHistoryRequest(String id, String method, ObjectNode params) {
                ObjectNode req = objectMapper.createObjectNode();
                req.put("type", "req");
                req.put("id", id);
                req.put("method", method);
                req.set("params", params);
                send(req.toString());
            }
        };

        if (!historyClient.connectBlocking(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("history WS connect timeout");
        }
        if (!ready.await(timeoutMs + 5000L, TimeUnit.MILLISECONDS)) {
            historyClient.close();
            throw new IllegalStateException("history WS timeout");
        }
        historyClient.close();
        if (error.get() != null) {
            throw error.get();
        }
        JsonNode payload = result.get();
        if (payload == null) {
            throw new IllegalStateException("chat.history empty response");
        }
        return payload;
    }

    private static ObjectNode historyParams(String sessionKey) {
        ObjectNode params = new ObjectMapper().createObjectNode();
        params.put("sessionKey", sessionKey);
        params.put("limit", 30);
        return params;
    }

    private static String formatException(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            return e.getClass().getSimpleName() + ": " + msg;
        }
        return e.getClass().getSimpleName();
    }

    private static boolean shouldFinish(AtomicReference<String> stream,
                                        AtomicReference<String> history,
                                        String baselineText,
                                        String completionNeedle) {
        String reply = pickReplyText(stream.get(), history.get(), baselineText);
        if (reply == null || reply.isBlank()) {
            return false;
        }
        if (completionNeedle != null) {
            return reply.contains(completionNeedle);
        }
        return true;
    }

    private static String previewReply(String reply) {
        if (reply == null) {
            return "";
        }
        String oneLine = reply.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() <= 160 ? oneLine : oneLine.substring(0, 160) + "…";
    }

    private static String pickReplyText(String streamText, String historyText, String baselineText) {
        String stream = streamText != null ? streamText.trim() : "";
        String history = historyText != null ? historyText.trim() : "";
        String baseline = baselineText != null ? baselineText.trim() : "";

        if (!history.isEmpty() && !baseline.isEmpty() && history.equals(baseline)) {
            return stream.isEmpty() ? null : stream;
        }
        if (!history.isEmpty() && !stream.isEmpty()) {
            return stream.length() > history.length() ? stream : history;
        }
        if (!history.isEmpty()) {
            return history;
        }
        return stream.isEmpty() ? null : stream;
    }

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

    public static boolean isLoopbackGateway(String gatewayHttpUrl) {
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

    public static String toWebSocketUrl(String gatewayHttpUrl) {
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

    private static boolean isChatTerminal(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return false;
        }
        if (payload.path("done").asBoolean(false)) {
            return true;
        }
        String state = payload.path("state").asText("").toLowerCase();
        return "final".equals(state)
                || "complete".equals(state)
                || "completed".equals(state)
                || "done".equals(state)
                || "error".equals(state);
    }

    private static void extractReplyFromPayload(JsonNode payload, AtomicReference<String> out) {
        captureChatAssistant(payload, out);
    }

    private static long deltaMs(long endMs, long startMs) {
        if (endMs <= 0L || startMs <= 0L) {
            return -1L;
        }
        return Math.max(0L, endMs - startMs);
    }
}
