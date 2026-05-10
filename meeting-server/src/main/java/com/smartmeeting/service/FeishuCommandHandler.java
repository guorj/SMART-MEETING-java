package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.MeetingCreateRequest;
import com.smartmeeting.api.dto.MeetingPresetResponse;
import com.smartmeeting.api.dto.MeetingResponse;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.MeetingStatus;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.entity.Voiceprint;
import com.smartmeeting.repository.VoiceprintMapper;
import com.smartmeeting.config.MeetingFeishuBotUxProperties;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.session.FeishuChatInstructionCardStore;
import com.smartmeeting.session.FeishuStartMeetingPendingStore;
import com.smartmeeting.session.FeishuUserLastGroupChatStore;
import com.smartmeeting.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 飞书命令处理器
 * 
 * 处理飞书对话指令的实际业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuCommandHandler {

    private final MeetingService meetingService;
    private final FeishuService feishuService;
    private final FeishuCardBuilder cardBuilder;
    private final FeishuCommandRouter commandRouter;
    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final JwtUtil jwtUtil;
    private final VoiceprintMapper voiceprintMapper;
    private final VoiceprintRegisterService voiceprintRegisterService;
    private final FeishuStartMeetingPendingStore startMeetingPendingStore;
    private final MeetingTypePresetService meetingTypePresetService;
    private final MeetingFeishuBotUxProperties feishuBotUxProperties;
    private final FeishuChatInstructionCardStore feishuChatInstructionCardStore;
    private final FeishuUserLastGroupChatStore feishuUserLastGroupChatStore;
    private final FeishuMeetingStartCoordinator feishuMeetingStartCoordinator;

    @Value("${meeting.base-url:http://localhost:8765}")
    private String baseUrl;

    /**
     * 完全自定义：开始会议 主题:… 参会人:…
     */
    public void handleStartMeeting(String openId, String chatId, String title, String participantsStr) {
        try {
            MeetingCreateRequest request = new MeetingCreateRequest();
            request.setTitle(title != null && !title.isBlank() ? title : "未命名会议");
            request.setCompany("默认集团");
            request.setGroupName("默认会议组");
            if (participantsStr != null && !participantsStr.isEmpty()) {
                List<MeetingCreateRequest.ParticipantEntry> participantEntries = new ArrayList<>();
                for (String name : participantsStr.split(",")) {
                    name = name.trim();
                    if (!name.isEmpty()) {
                        MeetingCreateRequest.ParticipantEntry entry = new MeetingCreateRequest.ParticipantEntry();
                        entry.setName(name);
                        entry.setUserId("unknown_" + name);
                        participantEntries.add(entry);
                    }
                }
                request.setParticipants(participantEntries);
            }
            doCreateAndStart(openId, chatId, request);
        } catch (Exception e) {
            log.error("开始会议命令处理失败", e);
            feishuService.sendMessage(chatId, "❌ 创建会议失败：" + e.getMessage());
        }
    }

    /**
     * 发送会议类型入口卡片：用户点击按钮在浏览器打开内嵌页（带短期 JWT），在 Web 上选类型并创建会议，不依赖卡片回传。
     */
    public void handleStartMeetingMenu(String openId, String chatId) {
        startMeetingPendingStore.clear(openId, chatId);
        try {
            String token = jwtUtil.generateFeishuWebStartMeetingEntryToken(openId, chatId);
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String entryUrl = base + "/start-meeting.html?token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
            String cardJson = cardBuilder.buildMeetingTypeWebEntryCard(entryUrl);
            feishuService.sendInteractiveCard(chatId, cardJson);
        } catch (Exception e) {
            log.error("发送会议类型入口卡片失败", e);
            feishuService.sendMessage(chatId, "❌ 无法打开发会务页面：" + e.getMessage()
                    + "\n请检查 meeting.base-url 是否为飞书可访问的 HTTPS 地址，或暂时使用「开始会议 1」等文字指令。");
        }
    }

    /** 消息卡片回传：选择预设类型或「其他会议」 */
    public void handleMeetingTypeCardAction(String openId, String chatId, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        startMeetingPendingStore.clear(openId, chatId);
        String cmd = value.path("cmd").asText("");
        if ("preset".equals(cmd)) {
            int code = value.path("code").asInt(0);
            if (code >= 1 && code <= 5) {
                handleStartMeetingPreset(openId, chatId, code);
            } else {
                log.warn("卡片回调 preset code 非法: {}", code);
            }
        } else if ("other_prompt".equals(cmd)) {
            handleStartMeetingOtherPrompt(openId, chatId);
        } else {
            log.warn("卡片回调未知 cmd: {}", cmd);
        }
    }

    /**
     * 进入类型选择菜单；若 {@code meeting.feishu.bot-ux.instruction-card-on-first-start} 为 true，则本群首次再先发常驻说明卡片。
     */
    public void handleStartMeetingEntryWithOptionalInstructionCard(String openId, String chatId) {
        if (feishuBotUxProperties.isInstructionCardOnFirstStart()
                && chatId != null
                && !chatId.isBlank()
                && feishuChatInstructionCardStore.markFirstInstructionIfAbsent(chatId)) {
            try {
                feishuService.sendInteractiveCard(chatId, cardBuilder.buildOnboardingInstructionCard());
            } catch (Exception e) {
                log.warn("发送首次说明卡片失败，仍继续会议类型菜单: {}", e.getMessage());
            }
        }
        handleStartMeetingMenu(openId, chatId);
    }

    /** 机器人被拉入群：欢迎语（需在开放平台订阅 im.chat.member.bot.added_v1） */
    public void handleBotJoinedChat(String chatId, String operatorOpenId) {
        if (!feishuBotUxProperties.isWelcomeOnBotJoin() || chatId == null || chatId.isBlank()) {
            return;
        }
        String text = feishuBotUxProperties.getWelcomeText();
        if (text == null || text.isBlank()) {
            return;
        }
        boolean ok = feishuService.sendMessage(chatId, text);
        log.info("机器人进群欢迎语已发送 chatId={}, ok={}, operatorOpenId={}", chatId, ok, operatorOpenId);
        // 拉人进群者常用菜单「推送事件」无 chat_id，此处预关联其当前群
        feishuUserLastGroupChatStore.record(operatorOpenId, chatId);
    }

    /**
     * 机器人自定义菜单「推送事件」（application.bot.menu_v6）。
     * 若事件体含群 chat_id，则等同用户在本群发送「开始会议」；否则私聊提示用户到群内使用菜单「发送消息」方式。
     */
    public void handleApplicationBotMenuV6(JsonNode event) {
        if (event == null) {
            return;
        }
        String eventKey = event.path("event_key").asText("");
        if (!feishuBotUxProperties.getMenuEventKeyStartMeeting().equals(eventKey)) {
            log.debug("未处理的菜单 event_key: {}", eventKey);
            return;
        }
        String openId = event.path("operator").path("operator_id").path("open_id").asText("");
        String chatId = resolveChatIdFromMenuEvent(event);
        if ((chatId == null || chatId.isBlank()) && feishuBotUxProperties.isMenuV6FallbackToLastGroupChat()) {
            chatId = feishuUserLastGroupChatStore.getLastChatId(openId);
            if (chatId != null && !chatId.isBlank()) {
                log.info("菜单 application.bot.menu_v6 无 chat_id，使用用户最近活跃群 chatId={} openId={}",
                        chatId, openId);
            }
        }
        if (chatId != null && !chatId.isBlank()) {
            handleStartMeetingEntryWithOptionalInstructionCard(openId, chatId);
        } else {
            String tip = "未关联到会话。请：① 与机器人**单聊里先发任意一条文字**；或 ② 订阅事件 **im.chat.access_event.bot_p2p_chat_entered_v1** 后重新进入单聊；"
                    + "③ 在**目标群**内发一条消息后再点菜单；也可把菜单改为「向当前会话发送消息」并填写：开始会议";
            feishuService.sendMessageToOpenId(openId, tip);
            log.info("菜单开始会议：无 chat_id 且无最近群记录，已私聊提示 openId={}", openId);
        }
    }

    private static String resolveChatIdFromMenuEvent(JsonNode event) {
        String c = event.path("chat_id").asText("").trim();
        if (!c.isEmpty()) {
            return c;
        }
        JsonNode chat = event.get("chat");
        if (chat != null) {
            if (chat.has("chat_id")) {
                return chat.path("chat_id").asText("").trim();
            }
            if (chat.has("id")) {
                return chat.path("id").asText("").trim();
            }
        }
        return "";
    }

    /** 菜单展示后，下一条「未知」消息：单字 1-5 为预设，否则整段为自定义主题 */
    public void handlePostMenuChoice(String openId, String chatId, String rawText) {
        startMeetingPendingStore.clear(openId, chatId);
        String t = rawText != null ? rawText.trim() : "";
        if (t.isEmpty() || "取消".equals(t)) {
            feishuService.sendMessage(chatId, "已取消。可再次发送「开始会议」。");
            return;
        }
        if (t.length() == 1) {
            char c = t.charAt(0);
            if (c >= '1' && c <= '5') {
                handleStartMeetingPreset(openId, chatId, c - '0');
                return;
            }
        }
        handleStartMeetingOtherWithTitle(openId, chatId, t);
    }

    public void handleStartMeetingPreset(String openId, String chatId, int typeCode) {
        try {
            MeetingCreateRequest request = new MeetingCreateRequest();
            request.setPresetTypeCode(typeCode);
            doCreateAndStart(openId, chatId, request);
        } catch (Exception e) {
            log.error("按预设开始会议失败", e);
            feishuService.sendMessage(chatId, "❌ 创建会议失败：" + e.getMessage());
        }
    }

    public void handleStartMeetingOtherPrompt(String openId, String chatId) {
        startMeetingPendingStore.markTypeSixThemePending(openId, chatId);
        feishuService.sendMessage(chatId,
                "已选择「其他会议」。请直接回复会议主题，或发送：主题:会议名称\n（30 分钟内有效，发送「开始会议 1」等可取消等待）");
    }

    public void handleStartMeetingOtherWithTitle(String openId, String chatId, String title) {
        try {
            startMeetingPendingStore.clear(openId, chatId);
            MeetingCreateRequest request = new MeetingCreateRequest();
            request.setPresetTypeCode(6);
            request.setTitle(title.trim());
            doCreateAndStart(openId, chatId, request);
        } catch (Exception e) {
            log.error("创建其他会议失败", e);
            feishuService.sendMessage(chatId, "❌ 创建会议失败：" + e.getMessage());
        }
    }

    /** 用户在选择「6」后，下一条文本作为主题 */
    public void handlePendingOtherMeetingTitle(String openId, String chatId, String rawText) {
        startMeetingPendingStore.clear(openId, chatId);
        String t = rawText.trim();
        if (t.startsWith("主题：") || t.startsWith("主题:")) {
            t = t.replaceFirst("^主题[:：]\\s*", "").trim();
        }
        if (t.isEmpty() || "取消".equals(t)) {
            feishuService.sendMessage(chatId, "已取消。请重新发送「开始会议」选择类型。");
            return;
        }
        handleStartMeetingOtherWithTitle(openId, chatId, t);
    }

    public void handleMeetingTypeShort(String openId, String chatId, int n) {
        if (n >= 1 && n <= 5) {
            handleStartMeetingPreset(openId, chatId, n);
        } else if (n == 6) {
            handleStartMeetingOtherPrompt(openId, chatId);
        } else {
            feishuService.sendMessage(chatId, "序号应为 1-6");
        }
    }

    private void doCreateAndStart(String openId, String chatId, MeetingCreateRequest request) {
        try {
            feishuMeetingStartCoordinator.createMeetingStartAndNotifyFeishu(openId, chatId, request);
        } catch (BusinessException e) {
            feishuService.sendMessage(chatId, e.getMessage());
        }
    }

    /**
     * 处理结束会议命令
     * 
     * 流程:
     * 1. 查找会议（指定ID或用户当前活跃会议）
     * 2. 校验权限（只有创建者可结束）
     * 3. 结束会议触发纪要生成
     * 4. 发送处理中卡片
     */
    public void handleStopMeeting(String openId, String chatId, String meetingId) {
        try {
            Meeting meeting;
            
            if (meetingId != null && !meetingId.isEmpty()) {
                meeting = meetingMapper.selectById(meetingId);
            } else {
                // 查找用户当前活跃会议
                LambdaQueryWrapper<Meeting> activeQuery = new LambdaQueryWrapper<>();
                activeQuery.eq(Meeting::getCreatorId, openId);
                activeQuery.in(Meeting::getStatus, 
                    MeetingStatus.STARTED.name(), 
                    MeetingStatus.RECORDING.name());
                meeting = meetingMapper.selectOne(activeQuery);
            }

            if (meeting == null) {
                feishuService.sendMessage(chatId, "未找到进行中的会议");
                return;
            }

            // 权限校验
            if (!meeting.getCreatorId().equals(openId)) {
                feishuService.sendMessage(chatId, "只有会议发起人可以结束会议");
                return;
            }

            // 状态校验
            String currentStatus = meeting.getStatus();
            if (!MeetingStatus.STARTED.name().equals(currentStatus) &&
                !MeetingStatus.RECORDING.name().equals(currentStatus)) {
                feishuService.sendMessage(chatId, 
                    "会议「" + meeting.getTitle() + "」当前状态为" + currentStatus + "，无需结束");
                return;
            }

            // 发送处理中卡片
            String processingCard = cardBuilder.buildProcessingCard(meeting.getTitle());
            feishuService.sendInteractiveCard(chatId, processingCard);

            // 结束会议（触发纪要生成）
            MeetingResponse response = meetingService.endMeeting(meeting.getId());

            log.info("会议已结束: meetingId={}, status={}", meeting.getId(), response.getStatus());

        } catch (Exception e) {
            log.error("结束会议命令处理失败", e);
            feishuService.sendMessage(chatId, "❌ 结束会议失败：" + e.getMessage());
        }
    }

    /**
     * 处理查看纪要命令
     */
    public void handleListMinutes(String openId, String chatId, int limit) {
        try {
            LambdaQueryWrapper<Meeting> query = new LambdaQueryWrapper<>();
            query.eq(Meeting::getCreatorId, openId);
            query.orderByDesc(Meeting::getCreatedAt);
            query.last("LIMIT " + limit);

            List<Meeting> meetings = meetingMapper.selectList(query);

            if (meetings.isEmpty()) {
                feishuService.sendMessage(chatId, "📋 暂无历史会议纪要");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📋 最近").append(meetings.size()).append("场会议纪要：\n\n");

            for (Meeting m : meetings) {
                String statusIcon = getStatusIcon(m.getStatus());
                String timeStr = m.getCreatedAt() != null ? 
                    m.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm")) : "?";
                
                sb.append(statusIcon).append(" ").append(m.getTitle()).append(" (").append(timeStr).append(")\n");
                sb.append("   🆔 ").append(m.getId()).append("\n");
                if (m.getDocUrl() != null) {
                    sb.append("   📄 ").append(m.getDocUrl()).append("\n");
                }
            }

            feishuService.sendMessage(chatId, sb.toString());

        } catch (Exception e) {
            log.error("查看纪要命令处理失败", e);
            feishuService.sendMessage(chatId, "❌ 查询失败：" + e.getMessage());
        }
    }

    /**
     * 处理加入会议命令
     */
    public void handleJoinMeeting(String openId, String chatId, String meetingId) {
        try {
            Meeting meeting;
            
            if (meetingId != null && !meetingId.isEmpty()) {
                meeting = meetingMapper.selectById(meetingId);
            } else {
                // 查找用户可见的活跃会议
                LambdaQueryWrapper<Meeting> activeQuery = new LambdaQueryWrapper<>();
                activeQuery.eq(Meeting::getChatId, chatId);
                activeQuery.in(Meeting::getStatus, 
                    MeetingStatus.STARTED.name(), 
                    MeetingStatus.RECORDING.name());
                meeting = meetingMapper.selectOne(activeQuery);
            }

            if (meeting == null) {
                feishuService.sendMessage(chatId, 
                    "❌ 未找到进行中的会议\n" +
                    "💡 请指定会议ID：加入会议 会议ID:mtg_xxxxx");
                return;
            }

            // 检查是否已加入
            LambdaQueryWrapper<Participant> pQuery = new LambdaQueryWrapper<>();
            pQuery.eq(Participant::getMeetingId, meeting.getId());
            pQuery.eq(Participant::getUserId, openId);
            Participant existing = participantMapper.selectOne(pQuery);

            if (existing != null) {
                feishuService.sendMessage(chatId, 
                    "ℹ️ 您已在会议「" + meeting.getTitle() + "」中");
                return;
            }

            // 添加参会人
            Participant participant = new Participant();
            participant.setId(UUID.randomUUID().toString());
            participant.setMeetingId(meeting.getId());
            participant.setUserId(openId);
            // TODO: 从飞书API获取用户姓名
            participant.setName("用户" + openId.substring(openId.length() - 6));
            participant.setStatus("JOINED");
            participant.setTodoCount(0);
            participant.setCompletedCount(0);
            participant.setVoiceprintReady(false);
            participantMapper.insert(participant);

            feishuService.sendMessage(chatId, 
                "✅ 已加入会议「" + meeting.getTitle() + "」\n" +
                "🆔 会议ID: " + meeting.getId());

            log.info("用户加入会议: openId={}, meetingId={}", openId, meeting.getId());

        } catch (Exception e) {
            log.error("加入会议命令处理失败", e);
            feishuService.sendMessage(chatId, "❌ 加入会议失败：" + e.getMessage());
        }
    }

    /** 用户主动发「帮助」等关键词时发送完整指令说明（混合方案：未知指令不再自动贴全文） */
    public void handleHelp(String chatId) {
        feishuService.sendMessage(chatId, commandRouter.getHelpText());
    }

    /**
     * 未识别文本：短提示，避免与会议卡片、误触消息叠成长篇说明。
     */
    public void handleUnknown(String chatId) {
        feishuService.sendMessage(chatId,
                "未识别该消息。可发「开始会议」使用会务，或发「帮助」查看全部指令。");
    }

    /**
     * 处理注册声纹命令
     * 
     * 流程:
     * 1. 获取用户姓名（从指令参数或飞书API）
     * 2. 检查是否已注册声纹
     * 3. 生成注册token和链接
     * 4. 发送飞书卡片（含注册链接）
     */
    public void handleRegisterVoiceprint(String openId, String chatId, String userName) {
        try {
            // 如果未提供姓名，从飞书API获取
            if (userName == null || userName.isEmpty()) {
                userName = feishuService.getUserName(openId);
                if (userName == null) {
                    userName = "用户" + openId.substring(openId.length() - 6);
                }
            }
            
            // 检查是否已注册
            Voiceprint existing = voiceprintMapper.selectOne(
                new LambdaQueryWrapper<Voiceprint>()
                    .eq(Voiceprint::getFeishuUserId, openId)
                    .gt(Voiceprint::getExpiresAt, LocalDateTime.now())
            );
            
            if (existing != null) {
                // 已注册，检查有效期
                boolean isValid = existing.getExpiresAt().isAfter(LocalDateTime.now().plusDays(30));
                if (isValid) {
                    feishuService.sendMessage(chatId, 
                        "您的声纹已注册，有效期至 " + existing.getExpiresAt().toLocalDate() + "\n" +
                        "如需重新注册，请先发送 删除声纹 删除旧声纹");
                    return;
                } else {
                    feishuService.sendMessage(chatId, 
                        "您的声纹即将过期（" + existing.getExpiresAt().toLocalDate() + "）\n" +
                        "建议重新注册以保持识别效果");
                }
            }
            
            // 创建注册session（生成token）
            String regToken = UUID.randomUUID().toString().replace("-", "");
            voiceprintRegisterService.createRegisterSession(regToken, openId, userName);
            
            // 生成注册链接
            String regUrl = baseUrl + "/voiceprint?token=" + regToken;
            
            // 发送注册卡片
            String card = cardBuilder.buildVoiceprintRegisterCard(userName, regUrl);
            feishuService.sendInteractiveCard(chatId, card);
            
            log.info("声纹注册链接已发送: openId={}, userName={}, token={}", openId, userName, regToken);
            
        } catch (Exception e) {
            log.error("注册声纹命令处理失败", e);
            feishuService.sendMessage(chatId, "❌ 注册声纹失败：" + e.getMessage());
        }
    }

    private String getStatusIcon(String status) {
        if (MeetingStatus.COMPLETED.name().equals(status)) return "✅";
        if (MeetingStatus.PROCESSING.name().equals(status)) return "⏳";
        if (MeetingStatus.RECORDING.name().equals(status)) return "🔴";
        if (MeetingStatus.TODO_TRACKING.name().equals(status)) return "📌";
        return "⏹️";
    }
}