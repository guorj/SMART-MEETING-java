package com.smartmeeting.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 飞书 Webhook 控制器
 * 
 * 接口:
 * - POST /api/v1/feishu/webhook - 接收飞书事件回调（可与「订阅回调」中的卡片回传共用同一 URL）
 * - POST /api/v1/feishu/callback - 可选：单独的消息卡片请求地址
 * 
 * 参考 Python 版 main.py 的 feishu_webhook
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/feishu")
public class FeishuWebhookController {
    private static final int BODY_PREVIEW_MAX = 400;

    private final FeishuCommandRouter commandRouter;
    private final FeishuCommandHandler commandHandler;
    private final FeishuStartMeetingPendingStore startMeetingPendingStore;
    private final FeishuUserLastGroupChatStore feishuUserLastGroupChatStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${meeting.feishu.encrypt-key:}")
    private String encryptKey;

    @Value("${meeting.feishu.verification-token:}")
    private String feishuVerificationToken;

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
     * 飞书事件回调入口
     * 接收: im.message.receive_v1 等事件
     * 
     * 飞书要求:
     * 1. URL 验证: 返回 challenge 字段
     * 2. 事件处理: 返回包含 "code": 0 的响应（1s超时）
     * 
     * 实际处理异步执行，避免超时
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
     * 处理飞书消息接收事件
     * 
     * 参考 Python 版 handle_text_command
     */
    private void handleImMessageReceive(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            JsonNode message = event.path("message");
            JsonNode sender = event.path("sender");

            String msgType = message.path("message_type").asText();
            String msgContent = message.path("content").asText();
            String chatId = message.path("chat_id").asText();
            String openId = sender.path("sender_id").path("open_id").asText();

            log.info("Feishu message received: type={}, chat={}, sender={}", msgType, chatId, openId);
            // 供「菜单 - 推送事件」无 chat_id 时回退到用户最近活跃会话（群或单聊）
            feishuUserLastGroupChatStore.record(openId, chatId);

            if ("text".equals(msgType)) {
                // 解析消息内容
                JsonNode content = objectMapper.readTree(msgContent);
                String text = content.path("text").asText();
                log.info("Feishu text message: {}", text);

                FeishuCommandRouter.CommandResult result = commandRouter.parse(text);
                Kind pendingKind = (openId != null && !openId.isEmpty())
                        ? startMeetingPendingStore.getKind(openId, chatId)
                        : null;
                if (pendingKind != null) {
                    if (pendingKind == Kind.TYPE6_THEME_PENDING) {
                        if ("unknown".equals(result.getCommand())) {
                            CompletableFuture.runAsync(() ->
                                    commandHandler.handlePendingOtherMeetingTitle(openId, chatId, text));
                            return;
                        }
                        startMeetingPendingStore.clear(openId, chatId);
                    } else if (pendingKind == Kind.POST_MENU_CHOICE) {
                        if ("unknown".equals(result.getCommand())) {
                            CompletableFuture.runAsync(() ->
                                    commandHandler.handlePostMenuChoice(openId, chatId, text));
                            return;
                        }
                        startMeetingPendingStore.clear(openId, chatId);
                    }
                }

                String command = result.getCommand();
                Map<String, String> params = result.getParams();

                switch (command) {
                    case "start_meeting_menu":
                        CompletableFuture.runAsync(() ->
                                commandHandler.handleStartMeetingEntryWithOptionalInstructionCard(openId, chatId));
                        break;
                    case "start_meeting_preset":
                        int typeCode = Integer.parseInt(params.get("type"));
                        CompletableFuture.runAsync(() ->
                                commandHandler.handleStartMeetingPreset(openId, chatId, typeCode));
                        break;
                    case "start_meeting_other_prompt":
                        CompletableFuture.runAsync(() ->
                                commandHandler.handleStartMeetingOtherPrompt(openId, chatId));
                        break;
                    case "start_meeting_other_with_title":
                        CompletableFuture.runAsync(() ->
                                commandHandler.handleStartMeetingOtherWithTitle(openId, chatId,
                                        params.get("title")));
                        break;
                    case "meeting_type_short":
                        int n = Integer.parseInt(params.get("num"));
                        CompletableFuture.runAsync(() ->
                                commandHandler.handleMeetingTypeShort(openId, chatId, n));
                        break;
                    case "start_meeting":
                        CompletableFuture.runAsync(() -> commandHandler.handleStartMeeting(openId, chatId,
                                params.get("title"), params.get("participants")));
                        break;
                        
                    case "stop_meeting":
                        commandHandler.handleStopMeeting(openId, chatId, 
                            params.get("meeting_id"));
                        break;
                        
                    case "list_minutes":
                        int limit = Integer.parseInt(params.getOrDefault("limit", "5"));
                        commandHandler.handleListMinutes(openId, chatId, limit);
                        break;
                        
                    case "join_meeting":
                        commandHandler.handleJoinMeeting(openId, chatId, 
                            params.get("meeting_id"));
                        break;
                        
                    case "register_voiceprint":
                        commandHandler.handleRegisterVoiceprint(openId, chatId, params.get("name"));
                        break;
                        
                    case "rename_speaker":
                        // TODO: 实现说话人修改
                        log.info("Rename speaker requested: meeting={}, old={}, new={}", 
                            params.get("meeting_id"), params.get("old_name"), params.get("new_name"));
                        break;
                        
                    case "regenerate_minutes":
                        // TODO: 实现重新生成纪要
                        log.info("Regenerate minutes requested: meeting={}", params.get("meeting_id"));
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

    /** 机器人被拉入群聊 */
    private void handleBotAddedToChat(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            String chatId = event.path("chat_id").asText("").trim();
            if (chatId.isEmpty()) {
                chatId = event.path("chat").path("chat_id").asText("").trim();
            }
            String operatorOpenId = event.path("operator_id").path("open_id").asText("").trim();
            if (operatorOpenId.isEmpty()) {
                operatorOpenId = event.path("operator").path("operator_id").path("open_id").asText("").trim();
            }
            commandHandler.handleBotJoinedChat(chatId, operatorOpenId);
        } catch (Exception e) {
            log.error("Failed to handle bot added to chat", e);
        }
    }

    /** 机器人自定义菜单（推送事件） */
    private void handleBotMenuV6(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            commandHandler.handleApplicationBotMenuV6(event);
        } catch (Exception e) {
            log.error("Failed to handle application.bot.menu_v6", e);
        }
    }

    /**
     * 标准包裹结构：{@code schema 2.0} + {@code header} + {@code event}（见「卡片回传交互回调」文档）。
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
     * 根级扁平结构：{@code schema=2.0} 且 {@code event_type} 为卡片回传，但无 {@code header}/{@code event} 包裹（部分投递通道）。
     * 根上 {@code token} 常为卡片更新凭证（c- 前缀），与 verification-token 不同，此处不按 header 校验。
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

    private ResponseEntity<Map<String, Object>> handleSchema20FlatCardCallback(JsonNode body, String traceId) {
        log.info("[traceId={}] Feishu card action (schema2 flat) eventType={}", traceId, body.path("event_type").asText(""));
        return dispatchCardMeetingActionFromEvent(body, traceId, "flat");
    }

    /**
     * 从「与官方 event 节点同形」的 JSON 上解析 operator/context/action，并异步执行业务、同步返回 toast。
     */
    private ResponseEntity<Map<String, Object>> dispatchCardMeetingActionFromEvent(JsonNode event, String traceId, String mode) {
        if (event == null || event.isMissingNode() || event.isNull()) {
            log.warn("[traceId={}] card action: empty event payload, mode={}", traceId, mode);
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "无效回调")));
        }
        String openId = event.path("operator").path("open_id").asText("").trim();
        if (openId.isEmpty()) {
            openId = event.path("operator").path("operator_id").path("open_id").asText("").trim();
        }
        if (openId.isEmpty()) {
            openId = event.path("operator_id").path("open_id").asText("").trim();
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
        JsonNode value = event.path("action").path("value");
        log.info("[traceId={}] Feishu card action ({}) openId={}, chatId={}, value={}",
                traceId, mode, openId, chatId, value);
        JsonNode finalValue = value;
        String finalOpenId = openId;
        String finalChatId = chatId;
        CompletableFuture.runAsync(() ->
                commandHandler.handleMeetingTypeCardAction(finalOpenId, finalChatId, finalValue));
        return ResponseEntity.ok(Map.of(
                "toast", Map.of("type", "success", "content", "已处理")));
    }

    private boolean verifyCardCallbackHeaderToken(JsonNode header) {
        if (feishuVerificationToken == null || feishuVerificationToken.isBlank()) {
            return true;
        }
        return feishuVerificationToken.equals(header.path("token").asText(""));
    }

    /** 旧版 {@code card.action.trigger_v1}：根级 open_id、open_message_id、action，无 header；根级 token 为更新凭证勿与 verification-token 混淆。 */
    private boolean isLegacyCardActionTriggerV1(JsonNode body) {
        return body != null
                && body.has("open_message_id")
                && body.has("action")
                && body.path("action").has("value")
                && !body.has("header");
    }

    private ResponseEntity<Map<String, Object>> handleCardActionTriggerV1(JsonNode body, String traceId) {
        String openId = body.path("open_id").asText("").trim();
        String chatId = body.path("open_chat_id").asText("").trim();
        if (chatId.isEmpty()) {
            chatId = feishuUserLastGroupChatStore.getLastChatId(openId);
        }
        JsonNode value = body.path("action").path("value");
        log.info("[traceId={}] Feishu card.action.trigger_v1 openId={}, chatId={}, value={}",
                traceId, openId, chatId, value);
        if (openId.isEmpty() || chatId.isEmpty()) {
            log.warn("[traceId={}] trigger_v1 缺少 open_id 或可推断的 chat_id", traceId);
            return ResponseEntity.ok(Map.of(
                    "toast", Map.of("type", "error", "content", "无法识别会话，请先在目标群内发一条消息后再点按钮")));
        }
        String finalOpenId = openId;
        String finalChatId = chatId;
        JsonNode finalValue = value;
        CompletableFuture.runAsync(() ->
                commandHandler.handleMeetingTypeCardAction(finalOpenId, finalChatId, finalValue));
        return ResponseEntity.ok(Map.of(
                "toast", Map.of("type", "success", "content", "已处理")));
    }

    /**
     * 用户进入与机器人的单聊（需订阅 im.chat.access_event.bot_p2p_chat_entered_v1，客户端约 v7.18+）。
     * 用于在无先发消息的情况下，将「推送事件」菜单关联到该私聊 chat_id。
     */
    private void handleBotP2pChatEntered(JsonNode body) {
        try {
            JsonNode event = body.path("event");
            String chatId = event.path("chat_id").asText("").trim();
            String openId = event.path("operator_id").path("open_id").asText("").trim();
            if (openId.isEmpty()) {
                openId = event.path("operator").path("operator_id").path("open_id").asText("").trim();
            }
            if (openId.isEmpty()) {
                openId = event.path("user").path("open_id").asText("").trim();
            }
            if (openId.isEmpty()) {
                openId = event.path("user_id").path("open_id").asText("").trim();
            }
            if (!chatId.isEmpty() && !openId.isEmpty()) {
                feishuUserLastGroupChatStore.record(openId, chatId);
                log.info("bot_p2p_chat_entered: 已记录单聊会话 openId={}, chatId={}", openId, chatId);
            } else {
                log.debug("bot_p2p_chat_entered: 缺少 chat_id 或 open_id，event={}", event);
            }
        } catch (Exception e) {
            log.error("Failed to handle im.chat.access_event.bot_p2p_chat_entered_v1", e);
        }
    }

    /**
     * 飞书事件解密（AES-256-CBC）
     *
     * 飞书加密算法：
     * - key = SHA256(encrypt_key) 取前32字节
     * - IV = Base64解码后encrypt字符串的前16字节
     * - 密文 = Base64解码后16字节之后
     *
     * 参考 Python 版 main.py 的 decrypt_feishu_event
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
     * 飞书消息卡片回传（含 URL 校验、card.action.trigger）。
     * 开发者后台「消息卡片请求地址」需指向本接口，例如：https://你的域名/api/v1/feishu/callback
     * <p>部分租户下飞书也会把「事件订阅」加密包投递到本 URL；解密后若非卡片事件则按与 /webhook 相同逻辑处理并返回 {@code code:0}。
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
                    String userId = event.path("operator").path("open_id").asText("");
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

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

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
}