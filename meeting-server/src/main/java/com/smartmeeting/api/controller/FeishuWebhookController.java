package com.smartmeeting.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.service.FeishuCommandHandler;
import com.smartmeeting.service.FeishuCommandRouter;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.session.FeishuStartMeetingPendingStore.Kind;
import com.smartmeeting.session.FeishuUserLastGroupChatStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书 Webhook 与卡片回调控制器。
 * <p>
 * 主要接口：
 * <ul>
 *   <li>{@code POST /api/v1/feishu/webhook} — 事件订阅回调（消息、机器人入群、菜单等）</li>
 *   <li>{@code POST /api/v1/feishu/callback} — 消息卡片交互回调（可与 webhook 共用或单独配置）</li>
 * </ul>
 * 飞书要求 1 秒内 ACK；业务逻辑异步执行。参考 Python 版 {@code main.py} 的 {@code feishu_webhook}。
 *
 * @see FeishuCommandRouter
 * @see FeishuCommandHandler
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/feishu")
public class FeishuWebhookController {
    private static final int BODY_PREVIEW_MAX = 400;

    /**
     * 临时兜底映射：当飞书回调中 user_id 为空时，按 open_id 映射到一个临时 userId，
     * 使该用户能正常使用会议管理等功能。待飞书侧修复该用户身份问题后移除。
     * <p>
     * key = open_id, value = 临时分配的 userId（用于会话状态、日志追踪）
     */
    // TODO 待飞书侧修复该用户身份问题后移除此映射及 handleImMessageReceive 中的兜底逻辑。
    private static final Map<String, String> OPEN_ID_TEMP_USER_MAP = new ConcurrentHashMap<>();
    static {
        OPEN_ID_TEMP_USER_MAP.put("ou_5809ce881ece7dabbccf8402848e32bd", "temp_user_ou_5809ce881ece7dabbccf8402848e32bd");
    }

    private final FeishuCommandRouter commandRouter;
    private final FeishuCommandHandler commandHandler;
    private final FeishuStartMeetingPendingStore startMeetingPendingStore;
    private final FeishuUserLastGroupChatStore feishuUserLastGroupChatStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${meeting.feishu.encrypt-key:}")
    private String encryptKey;

    @Value("${meeting.feishu.verification-token:}")
    private String feishuVerificationToken;

    /**
     * 构造注入飞书命令路由与待办状态存储等依赖。
     *
     * @param commandRouter              文本指令解析路由
     * @param commandHandler             指令与卡片动作业务处理
     * @param startMeetingPendingStore   多步建会会话状态
     * @param feishuUserLastGroupChatStore 用户最近活跃会话（卡片无 chatId 时回退）
     */
    public FeishuWebhookController(FeishuCommandRouter commandRouter,
                                   FeishuCommandHandler commandHandler,
                                   FeishuStartMeetingPendingStore startMeetingPendingStore,
                                   FeishuUserLastGroupChatStore feishuUserLastGroupChatStore) {
        this.commandRouter = commandRouter;
        this.commandHandler = commandHandler;
        this.startMeetingPendingStore = startMeetingPendingStore;
        this.feishuUserLastGroupChatStore = feishuUserLastGroupChatStore;
    }

    /**
     * 飞书事件回调入口，接收 {@code im.message.receive_v1} 等事件。
     * <p>
     * 飞书要求：URL 验证返回 {@code challenge}；事件处理返回 {@code code:0}（1s 超时）。
     * 实际业务异步执行，避免超时。
     *
     * @param rawBody 原始 JSON 请求体（可能含 {@code encrypt} 字段）
     * @return challenge 验证响应，或 {@code {code:0}} 成功 ACK
     */
    @PostMapping({"/webhook", "/webhook/"})
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody String rawBody) {
        String traceId = newTraceId();
        try {
            log.debug("[traceId={}] Feishu webhook raw received: size={}, preview={}",
                    traceId, rawBody != null ? rawBody.length() : 0, previewBody(rawBody));
            JsonNode body = objectMapper.readTree(rawBody);
            log.debug("[traceId={}] Feishu webhook body shape: {}", traceId, describeBodyShape(body));

            // 0. 事件解密（飞书加密模式）
            if (body.has("encrypt")) {
                if (encryptKey != null && !encryptKey.isEmpty()) {
                    try {
                        body = decryptFeishuEvent(body.get("encrypt").asText());
                        log.debug("[traceId={}] Feishu event decrypted successfully", traceId);
                    } catch (Exception e) {
                        log.error("[traceId={}] Feishu event decryption failed", traceId, e);
                        return ResponseEntity.ok(Map.of("code", 0, "msg", "decrypt failed"));
                    }
                } else {
                    log.error("[traceId={}] Feishu sent encrypted event but encrypt-key is not configured", traceId);
                    return ResponseEntity.ok(Map.of("code", 0, "msg", "encrypt key not configured"));
                }
            }

            // 1. URL 验证 (challenge)
            if (body.has("challenge")) {
                String challenge = body.get("challenge").asText();
                log.info("[traceId={}] Feishu URL verification challenge: {}",
                        traceId, challenge.substring(0, Math.min(20, challenge.length())));
                return ResponseEntity.ok(Map.of("challenge", challenge));
            }

            // schema 2.0 但根级扁平的卡片回调（无 header/event 包裹，见飞书「配置卡片交互」等文档中的回调示例）
            if (isSchema20FlatCardCallback(body)) {
                return handleSchema20FlatCardCallback(body, traceId);
            }

            // 旧版卡片回传 trigger_v1：扁平 JSON、无 header；与新版同时订阅时飞书会并发 POST，须返回 toast 而非 code:0（否则客户端 200080）
            if (isLegacyCardActionTriggerV1(body)) {
                return handleCardActionTriggerV1(body, traceId);
            }

            // 2. 事件处理
            if (body.has("header")) {
                JsonNode header = body.get("header");
                String eventType = header.path("event_type").asText();
                String eventId = header.path("event_id").asText();

                if ("card.action.trigger".equals(eventType) || "card.action.trigger_v1".equals(eventType)) {
                    log.info("[traceId={}] Feishu webhook: card callback eventType={}, eventId={}",
                            traceId, eventType, eventId);
                    return handleWrappedCardActionTrigger(body, traceId);
                }

                log.debug("[traceId={}] Received feishu event: type={}, id={}", traceId, eventType, eventId);

                // 处理消息事件 - 异步执行避免超时
                if ("im.message.receive_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleImMessageReceive(finalBody));
                } else if ("im.chat.member.bot.added_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotAddedToChat(finalBody));
                } else if ("application.bot.menu_v6".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotMenuV6(finalBody));
                } else if ("im.chat.access_event.bot_p2p_chat_entered_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotP2pChatEntered(finalBody));
                }

                // 返回成功响应（飞书要求，必须在1s内返回）
                return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
            }

            // 未知格式
            log.warn("[traceId={}] Unknown webhook format, shape={}, bodyPreview={}",
                    traceId, describeBodyShape(body), previewBody(body.toString()));
            return ResponseEntity.ok(Map.of("code", 0));

        } catch (Exception e) {
            log.error("[traceId={}] Failed to handle feishu webhook, rawPreview={}",
                    traceId, previewBody(rawBody), e);
            return ResponseEntity.ok(Map.of("code", 0, "msg", "error: " + e.getMessage()));
        }
    }

    /**
     * 处理飞书 {@code im.message.receive_v1} 消息接收事件（异步）。
     * <p>
     * 解析文本指令并路由至 {@link FeishuCommandHandler}，参考 Python 版 {@code handle_text_command}。
     *
     * @param body 解密后的完整事件 JSON
     */
    private void handleImMessageReceive(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            JsonNode message = event.path("message");
            JsonNode sender = event.path("sender");

            String msgType = message.path("message_type").asText();
            String msgContent = message.path("content").asText();
            String chatId = message.path("chat_id").asText();
            String userId = resolveSenderUserId(sender.path("sender_id"));

            log.info("Feishu message received: type={}, chat={}, sender={}", msgType, chatId, userId);
            // 供「菜单 - 推送事件」无 chat_id 时回退到用户最近活跃会话（群或单聊）
            feishuUserLastGroupChatStore.record(userId, chatId);

            if ("text".equals(msgType)) {
                // 解析消息内容
                JsonNode content = objectMapper.readTree(msgContent);
                String text = content.path("text").asText();
                log.info("Feishu text message: {}", text);

                FeishuCommandRouter.CommandResult result = commandRouter.parse(text);
                Kind pendingKind = (userId != null && !userId.isEmpty())
                        ? startMeetingPendingStore.getKind(userId, chatId)
                        : null;
                if (pendingKind != null) {
                    if (pendingKind == Kind.TYPE6_THEME_PENDING) {
                        if ("unknown".equals(result.getCommand())) {
                            CompletableFuture.runAsync(() ->
                                    commandHandler.handlePendingOtherMeetingTitle(userId, chatId, text));
                            return;
                        }
                        startMeetingPendingStore.clear(userId, chatId);
                    } else if (pendingKind == Kind.POST_MENU_CHOICE) {
                        if ("unknown".equals(result.getCommand())) {
                            CompletableFuture.runAsync(() ->
                                    commandHandler.handlePostMenuChoice(userId, chatId, text));
                            return;
                        }
                        startMeetingPendingStore.clear(userId, chatId);
                    }
                }

                String command = result.getCommand();
                Map<String, String> params = result.getParams();

                if ("open_dashboard".equals(command) && (userId == null || userId.isBlank())) {
                    log.warn("open_dashboard ignored because sender user_id is empty, sender={}, message={}",
                            sender.path("sender_id"), message);
                    commandHandler.handleUnknown(chatId);
                    return;
                }

                switch (command) {
                    case "open_dashboard":
                        CompletableFuture.runAsync(() -> commandHandler.handleOpenDashboard(userId, chatId));
                        break;
                    case "stop_meeting":
                        commandHandler.handleStopMeeting(userId, chatId, params.get("meeting_id"));
                        break;
                    case "rename_speaker":
                        commandHandler.handleRenameSpeaker(chatId,
                                params.get("meeting_id"), params.get("old_name"), params.get("new_name"));
                        break;
                    case "regenerate_minutes":
                        commandHandler.handleRegenerateMinutes(chatId, params.get("meeting_id"));
                        break;
                    case "show_help":
                        CompletableFuture.runAsync(() -> commandHandler.handleHelp(chatId));
                        break;
                    case "unknown":
                        commandHandler.handleUnknown(chatId);
                        break;
                }
            }

        } catch (Exception e) {
            log.error("Failed to handle im message receive", e);
        }
    }

    /**
     * 处理机器人被拉入群聊事件 {@code im.chat.member.bot.added_v1}。
     *
     * @param body 事件 JSON
     */
    private void handleBotAddedToChat(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            String chatId = event.path("chat_id").asText("").trim();
            if (chatId.isEmpty()) {
                chatId = event.path("chat").path("chat_id").asText("").trim();
            }
            String operatorUserId = resolvePrincipalUserId(event.path("operator_id"));
            if (operatorUserId.isEmpty()) {
                operatorUserId = resolvePrincipalUserId(event.path("operator").path("operator_id"));
            }
            commandHandler.handleBotJoinedChat(chatId, operatorUserId);
        } catch (Exception e) {
            log.error("Failed to handle bot added to chat", e);
        }
    }

    /**
     * 处理机器人自定义菜单事件 {@code application.bot.menu_v6}（推送事件入口）。
     *
     * @param body 事件 JSON
     */
    private void handleBotMenuV6(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            commandHandler.handleApplicationBotMenuV6(event);
        } catch (Exception e) {
            log.error("Failed to handle application.bot.menu_v6", e);
        }
    }

    /**
     * 标准包裹结构的卡片回传：{@code schema 2.0} + {@code header} + {@code event}。
     *
     * @param body    完整回调 JSON
     * @param traceId 链路追踪 ID
     * @return 含 toast 的 HTTP 200 响应
     */
    private ResponseEntity<Map<String, Object>> handleWrappedCardActionTrigger(JsonNode body, String traceId) {
        JsonNode header = body.path("header");
        if (!verifyCardCallbackHeaderToken(header)) {
            log.warn("[traceId={}] card action trigger: header token mismatch, eventType={}, shape={}",
                    traceId, header.path("event_type").asText(""), describeBodyShape(body));
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "校验失败")));
        }
        return dispatchCardMeetingActionFromEvent(body.path("event"), traceId, "wrapped");
    }

    /**
     * 判断是否为 schema 2.0 根级扁平卡片回调（无 header/event 包裹）。
     *
     * @param body 解析后的 JSON
     * @return 符合扁平卡片形态时为 true
     */
    private boolean isSchema20FlatCardCallback(JsonNode body) {
        if (body == null || body.isNull()) {
            return false;
        }
        if (!"2.0".equals(body.path("schema").asText(""))) {
            return false;
        }
        if (body.has("header") && !body.get("header").isNull()) {
            return false;
        }
        String et = body.path("event_type").asText("");
        if (!"card.action.trigger".equals(et) && !"card.action.trigger_v1".equals(et)) {
            return false;
        }
        return body.has("action");
    }

    /**
     * 处理 schema 2.0 扁平卡片回调。
     *
     * @param body    根级含 action 的 JSON
     * @param traceId 链路追踪 ID
     * @return 含 toast 的 HTTP 200 响应
     */
    private ResponseEntity<Map<String, Object>> handleSchema20FlatCardCallback(JsonNode body, String traceId) {
        log.info("[traceId={}] Feishu card action (schema2 flat) eventType={}", traceId, body.path("event_type").asText(""));
        return dispatchCardMeetingActionFromEvent(body, traceId, "flat");
    }

    /**
     * 从与官方 event 节点同形的 JSON 解析 operator/context/action，异步执行业务并同步返回 toast。
     *
     * @param event   事件或扁平根节点
     * @param traceId 链路追踪 ID
     * @param mode    投递模式标识（wrapped / flat），仅用于日志
     * @return 含 toast 的 HTTP 200 响应
     */
    private ResponseEntity<Map<String, Object>> dispatchCardMeetingActionFromEvent(JsonNode event, String traceId, String mode) {
        if (event == null || event.isMissingNode() || event.isNull()) {
            log.warn("[traceId={}] card action: empty event payload, mode={}", traceId, mode);
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "无效回调")));
        }
        String openId = resolvePrincipalUserId(event.path("operator"));
        if (openId.isEmpty()) {
            openId = resolvePrincipalUserId(event.path("operator").path("operator_id"));
        }
        if (openId.isEmpty()) {
            openId = resolvePrincipalUserId(event.path("operator_id"));
        }
        String chatId = event.path("context").path("open_chat_id").asText("").trim();
        if (chatId.isEmpty()) {
            chatId = event.path("context").path("chat_id").asText("").trim();
        }
        if (chatId.isEmpty() && !openId.isEmpty()) {
            chatId = feishuUserLastGroupChatStore.getLastChatId(openId);
            if (chatId != null && !chatId.isBlank()) {
                log.info("[traceId={}] card action ({}): context.open_chat_id empty, using last chatId={}",
                        traceId, mode, chatId);
            }
        }
        JsonNode action = event.path("action");
        JsonNode value = action.path("value");
        JsonNode payload = enrichActionValueWithForm(value, action.path("form_value"));
        log.info("[traceId={}] Feishu card action ({}) openId={}, chatId={}, value={}",
                traceId, mode, openId, chatId, payload);
        JsonNode finalValue = payload;
        String finalOpenId = openId;
        String finalChatId = chatId;
        CompletableFuture.runAsync(() ->
                commandHandler.handleMeetingTypeCardAction(finalOpenId, finalChatId, finalValue));
        return ResponseEntity.ok(Map.of(
                "toast", Map.of("type", "success", "content", "已处理")));
    }

    /**
     * 校验卡片回调 header 中的 verification token。
     *
     * @param header 事件头节点
     * @return 未配置 token 或匹配成功时为 true
     */
    private boolean verifyCardCallbackHeaderToken(JsonNode header) {
        if (feishuVerificationToken == null || feishuVerificationToken.isBlank()) {
            return true;
        }
        return feishuVerificationToken.equals(header.path("token").asText(""));
    }

    /**
     * 判断是否为旧版 {@code card.action.trigger_v1} 扁平结构（含 open_message_id、无 header）。
     *
     * @param body 解析后的 JSON
     * @return 符合旧版 trigger_v1 形态时为 true
     */
    private boolean isLegacyCardActionTriggerV1(JsonNode body) {
        return body != null
                && body.has("open_message_id")
                && body.has("action")
                && body.path("action").has("value")
                && !body.has("header");
    }

    /**
     * 处理旧版 {@code card.action.trigger_v1} 卡片回传。
     *
     * @param body    扁平 JSON
     * @param traceId 链路追踪 ID
     * @return 含 toast 的 HTTP 200 响应
     */
    private ResponseEntity<Map<String, Object>> handleCardActionTriggerV1(JsonNode body, String traceId) {
        String openId = body.path("user_id").asText("").trim();
        String chatId = body.path("open_chat_id").asText("").trim();
        if (chatId.isEmpty()) {
            chatId = feishuUserLastGroupChatStore.getLastChatId(openId);
        }
        JsonNode action = body.path("action");
        JsonNode value = action.path("value");
        JsonNode payload = enrichActionValueWithForm(value, action.path("form_value"));
        log.info("[traceId={}] Feishu card.action.trigger_v1 openId={}, chatId={}, value={}",
                traceId, openId, chatId, payload);
        if (openId.isEmpty() || chatId.isEmpty()) {
            log.warn("[traceId={}] trigger_v1 缺少 user_id/open_id 或可推断的 chat_id", traceId);
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "无法识别会话，请先在目标群内发一条消息后再点按钮")));
        }
        String finalOpenId = openId;
        String finalChatId = chatId;
        JsonNode finalValue = payload;
        CompletableFuture.runAsync(() ->
                commandHandler.handleMeetingTypeCardAction(finalOpenId, finalChatId, finalValue));
        return ResponseEntity.ok(Map.of(
                "toast", Map.of("type", "success", "content", "已处理")));
    }

    /**
     * 处理用户进入机器人单聊事件 {@code im.chat.access_event.bot_p2p_chat_entered_v1}。
     * <p>
     * 用于在无先发消息的情况下，将「推送事件」菜单关联到该私聊 chat_id。
     *
     * @param body 事件 JSON
     */
    private void handleBotP2pChatEntered(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            String chatId = event.path("chat_id").asText("").trim();
            String openId = resolvePrincipalUserId(event.path("operator_id"));
            if (openId.isEmpty()) {
                openId = resolvePrincipalUserId(event.path("operator").path("operator_id"));
            }
            if (openId.isEmpty()) {
                openId = resolvePrincipalUserId(event.path("user"));
            }
            if (openId.isEmpty()) {
                openId = event.path("user_id").asText("").trim();
            }
            if (!chatId.isEmpty() && !openId.isEmpty()) {
                feishuUserLastGroupChatStore.record(openId, chatId);
                log.info("bot_p2p_chat_entered: 已记录单聊会话 openId={}, chatId={}", openId, chatId);
            } else {
                log.debug("bot_p2p_chat_entered: 缺少 chat_id 或 user_id/open_id，event={}", event);
            }
        } catch (Exception e) {
            log.error("Failed to handle im.chat.access_event.bot_p2p_chat_entered_v1", e);
        }
    }

    /**
     * 飞书事件解密（AES-256-CBC）。
     * <p>
     * key = SHA256(encrypt_key) 前 32 字节；IV = Base64 解码后前 16 字节；密文为之后部分。
     * 参考 Python 版 {@code decrypt_feishu_event}。
     *
     * @param encryptStr 根级 encrypt 字段值
     * @return 解密后的 JSON 节点
     * @throws Exception 解密或 JSON 解析失败时
     */
    private JsonNode decryptFeishuEvent(String encryptStr) throws Exception {
        // Key: SHA256(encrypt_key) 取前32字节
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha256.digest(encryptKey.getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, 32);

        // Base64解码
        byte[] encrypted = Base64.getDecoder().decode(encryptStr);

        // IV: 前16字节
        byte[] iv = new byte[16];
        System.arraycopy(encrypted, 0, iv, 0, 16);

        // 密文: 16字节之后
        byte[] ciphertext = new byte[encrypted.length - 16];
        System.arraycopy(encrypted, 16, ciphertext, 0, ciphertext.length);

        // AES-256-CBC解密
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(ciphertext);

        return objectMapper.readTree(decrypted);
    }

    /**
     * 飞书消息卡片回传入口（含 URL 校验、{@code card.action.trigger}）。
     * <p>
     * 开发者后台「消息卡片请求地址」需指向本接口，例如：{@code https://域名/api/v1/feishu/callback}。
     * 部分租户下飞书也会把「事件订阅」加密包投递到本 URL；解密后若非卡片事件则与 {@link #handleWebhook} 对齐处理。
     *
     * @param rawBody 原始 JSON 请求体
     * @return challenge、toast 或 {@code {code:0}} 响应
     */
    @PostMapping({"/callback", "/callback/"})
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody String rawBody) {
        String traceId = newTraceId();
        try {
            log.info("[traceId={}] Feishu /callback POST received, rawLength={}",
                    traceId, rawBody != null ? rawBody.length() : 0);
            log.debug("[traceId={}] Feishu callback raw preview={}", traceId, previewBody(rawBody));
            JsonNode body = objectMapper.readTree(rawBody);
            log.info("[traceId={}] Feishu /callback parsed body shape: {}", traceId, describeBodyShape(body));

            if (body.has("encrypt") && encryptKey != null && !encryptKey.isEmpty()) {
                try {
                    body = decryptFeishuEvent(body.get("encrypt").asText());
                    log.info("[traceId={}] Feishu /callback decrypted body shape: {}", traceId, describeBodyShape(body));
                } catch (Exception e) {
                    log.error("[traceId={}] Feishu card callback decrypt failed", traceId, e);
                    return ResponseEntity.ok(Map.of("toast", Map.of("type", "error", "content", "decrypt failed")));
                }
            }

            if (isSchema20FlatCardCallback(body)) {
                return handleSchema20FlatCardCallback(body, traceId);
            }

            if (isLegacyCardActionTriggerV1(body)) {
                return handleCardActionTriggerV1(body, traceId);
            }

            // 配置「消息卡片请求地址」时的 URL 验证
            if ("url_verification".equals(body.path("type").asText()) && body.has("challenge")) {
                String token = body.path("token").asText("");
                if (feishuVerificationToken != null
                        && !feishuVerificationToken.isBlank()
                        && !feishuVerificationToken.equals(token)) {
                    log.warn("[traceId={}] Card url_verification token mismatch", traceId);
                }
                return ResponseEntity.ok(Map.of("challenge", body.get("challenge").asText()));
            }

            JsonNode header = body.path("header");
            String eventType = header.path("event_type").asText("");

            if ("card.action.trigger".equals(eventType) || "card.action.trigger_v1".equals(eventType)) {
                return handleWrappedCardActionTrigger(body, traceId);
            }

            /*
             * 飞书可能把「事件订阅」的加密包也 POST 到「消息卡片请求地址」/callback（与 webhook 同结构的 schema 2.0）。
             * 若只按卡片解析会落到 Unknown；此处与 /webhook 对齐：异步处理并返回 code:0。
             */
            if (body.has("header") && !eventType.isEmpty()) {
                if ("im.message.receive_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleImMessageReceive(finalBody));
                    log.info("[traceId={}] Feishu /callback delegated as event subscription: {}", traceId, eventType);
                    return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
                }
                if ("im.chat.member.bot.added_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotAddedToChat(finalBody));
                    log.info("[traceId={}] Feishu /callback delegated as event subscription: {}", traceId, eventType);
                    return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
                }
                if ("application.bot.menu_v6".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotMenuV6(finalBody));
                    log.info("[traceId={}] Feishu /callback delegated as event subscription: {}", traceId, eventType);
                    return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
                }
                if ("im.chat.access_event.bot_p2p_chat_entered_v1".equals(eventType)) {
                    JsonNode finalBody = body;
                    CompletableFuture.runAsync(() -> handleBotP2pChatEntered(finalBody));
                    log.info("[traceId={}] Feishu /callback delegated as event subscription: {}", traceId, eventType);
                    return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
                }
                log.warn("[traceId={}] Feishu /callback: non-card event_type={}, ack code=0 (check console URL config)",
                        traceId, eventType);
                return ResponseEntity.ok(Map.of("code", 0, "msg", "success"));
            }

            // 旧版或其它卡片结构（字符串 value）
            JsonNode event = body.path("event");
            if (!event.isMissingNode() && !event.isNull()) {
                JsonNode valueNode = event.path("action").path("value");
                if (valueNode.isTextual()) {
                    String actionName = valueNode.asText();
                    String userId = resolvePrincipalUserId(event.path("operator"));
                    log.info("[traceId={}] Feishu legacy card callback: action={}, userId={}",
                            traceId, actionName, userId);
                    switch (actionName) {
                        case "confirm_attend":
                            log.info("[traceId={}] User {} confirmed attendance", traceId, userId);
                            break;
                        case "decline_attend":
                            log.info("[traceId={}] User {} declined attendance", traceId, userId);
                            break;
                        case "complete_todo":
                            log.info("[traceId={}] User {} completed todo", traceId, userId);
                            break;
                        default:
                            log.warn("[traceId={}] Unknown legacy card value: {}", traceId, actionName);
                    }
                    return ResponseEntity.ok(Map.of(
                            "toast", Map.of("type", "success", "content", "操作成功")));
                }
            }

            log.warn("[traceId={}] Unknown card callback body: {}", traceId, body);
            Map<String, Object> toast = new HashMap<>();
            toast.put("type", "warning");
            toast.put("content", "未识别的回调");
            return ResponseEntity.ok(Map.of("toast", toast));

        } catch (Exception e) {
            log.error("[traceId={}] Failed to handle feishu callback, rawPreview={}",
                    traceId, previewBody(rawBody), e);
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "操作失败")));
        }
    }

    /** 生成 12 位十六进制链路追踪 ID。 */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 截断并转义请求体预览，用于日志（最大 {@value #BODY_PREVIEW_MAX} 字符）。
     *
     * @param rawBody 原始字符串
     * @return 安全预览文本
     */
    private String previewBody(String rawBody) {
        if (rawBody == null) {
            return "<null>";
        }
        String normalized = rawBody.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= BODY_PREVIEW_MAX) {
            return normalized;
        }
        return normalized.substring(0, BODY_PREVIEW_MAX) + "...(truncated)";
    }

    /**
     * 描述 JSON 体的结构特征，便于排查飞书回调格式差异。
     *
     * @param body 解析后的 JSON
     * @return 结构摘要字符串
     */
    private String describeBodyShape(JsonNode body) {
        if (body == null || body.isNull()) {
            return "null";
        }
        return String.format(
                "schema=%s,rootEventType=%s,hasHeader=%s,headerEventType=%s,hasEvent=%s,hasEncrypt=%s,"
                        + "hasChallenge=%s,hasRootAction=%s,hasEventAction=%s,hasOpenMessageId=%s",
                body.path("schema").asText(""),
                body.path("event_type").asText(""),
                body.has("header"),
                body.path("header").path("event_type").asText(""),
                body.has("event"),
                body.has("encrypt"),
                body.has("challenge"),
                body.has("action"),
                body.path("event").has("action"),
                body.has("open_message_id"));
    }

    /**
     * 从消息发送者身份节点解析 userId；当 {@code user_id} 为空时尝试临时 open_id 映射兜底。
     *
     * @param senderId 飞书 {@code sender_id} 节点
     * @return 主身份 userId，或临时映射 userId；均无则空串
     */
    private String resolveSenderUserId(JsonNode senderId) {
        String userId = resolvePrincipalUserId(senderId);
        if (!userId.isEmpty()) {
            return userId;
        }
        // TODO 待飞书侧修复该用户身份问题后移除（OPEN_ID_TEMP_USER_MAP 与此段兜底逻辑）。
        String openId = senderId.path("open_id").asText("").trim();
        String fallback = OPEN_ID_TEMP_USER_MAP.get(openId);
        if (fallback != null) {
            log.warn("user_id empty, apply temp open_id fallback: openId={}, fallbackUserId={}",
                    openId, fallback);
            return fallback;
        }
        return userId;
    }

    /**
     * 从飞书身份节点解析主身份：仅 user_id。
     */
    private String resolvePrincipalUserId(JsonNode idNode) {
        if (idNode == null || idNode.isMissingNode() || idNode.isNull()) {
            return "";
        }
        if (idNode.isTextual()) {
            return idNode.asText("").trim();
        }
        String direct = idNode.asText("").trim();
        if (!direct.isEmpty() && !idNode.isObject()) {
            return direct;
        }
        return idNode.path("user_id").asText("").trim();
    }

    private JsonNode enrichActionValueWithForm(JsonNode value, JsonNode formValue) {
        if ((formValue == null || formValue.isMissingNode() || formValue.isNull())
                || (value != null && !value.isObject() && !value.isMissingNode() && !value.isNull())) {
            return value;
        }
        ObjectNode out = objectMapper.createObjectNode();
        if (value != null && value.isObject()) {
            out.setAll((ObjectNode) value);
        }
        out.set("formValue", formValue);
        return out;
    }
}