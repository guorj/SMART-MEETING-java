package com.smartmeeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 飞书 Interactive Card JSON 构建器，对应 Python 版 feishu/message.py 的 build_*_card 函数。
 * <p>
 * 主要协作组件：{@link ObjectMapper}（构建卡片 JSON 节点）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuCardBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 构建「会议已开始」通知卡片（无「开始录音」按钮；发起人在 Web 会议主页拾音）。
     *
     * @param meetingId    会议 ID
     * @param title        会议主题
     * @param recordingUrl 会议主页操作员入口链接 /rec（可为 null）
     * @return 卡片 JSON 字符串
     */
    public String buildMeetingStartedNotifyCard(String meetingId, String title, String recordingUrl) {
        ObjectNode card = objectMapper.createObjectNode();

        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);

        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "🎙️ 智能会议已开始");
        header.put("template", "blue");

        ArrayNode elements = card.putArray("elements");

        addMarkdownElement(elements, "**📋 主题**：" + title);
        String nowStr = LocalDateTime.now().format(TIME_FMT);
        addMarkdownElement(elements, "**🕐 开始时间**：" + nowStr);
        addMarkdownElement(elements, "**🆔 会议ID**：" + meetingId);
        addDividerElement(elements);
        addMarkdownElement(elements,
                "**📱 录音**：发起人已在网页 **会议主页** 拾音（点「开始会议」同时启动录音与主持），**无需在本卡片上点击「开始录音」**。");
        addDividerElement(elements);
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            addActionButton(elements, "打开会议主页（直达）", recordingUrl, "primary");
            addDividerElement(elements);
        }

        ArrayList<String> notes = new ArrayList<>();
        notes.add("📌 请在会议主页点击「结束会议」生成纪要（飞书发「结束会议」仍为备用）");
        notes.add("⏱️ 最大录音时长4小时");
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            notes.add("✋ 会议主页链接（操作员入口 /rec）：" + recordingUrl);
        }
        addNoteElement(elements, notes);

        return card.toString();
    }

    /**
     * 构建线上参会人个人入会链接卡片（单聊推送，打开即登记到场、不推流）。
     *
     * @param meetingTitle    会议主题
     * @param participantName 参会人姓名
     * @param joinUrl         个人入会链接
     * @return 卡片 JSON 字符串
     */
    public String buildPersonalOnlineJoinCard(String meetingTitle, String participantName, String joinUrl) {
        ObjectNode card = objectMapper.createObjectNode();

        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);

        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "📲 线上到会确认");
        header.put("template", "turquoise");

        ArrayNode elements = card.putArray("elements");
        addMarkdownElement(elements,
                "**会议**：" + (meetingTitle != null ? meetingTitle : "") + "\n"
                        + "**参会人**：" + (participantName != null ? participantName : "") + "\n\n"
                        + "请点击下方按钮打开**个人链接**完成线上到场登记（无需麦克风）。");
        addDividerElement(elements);
        if (joinUrl != null && !joinUrl.isBlank()) {
            addActionButton(elements, "打开个人入会链接", joinUrl, "primary");
        }
        addNoteElement(elements, List.of(
                "请勿转发本链接；登记后由现场设备负责录音。",
                "若按钮无法打开，请复制链接到浏览器：" + (joinUrl != null ? joinUrl : "")
        ));
        return card.toString();
    }

    /**
     * 构建常驻操作说明卡片（首次在本群触发「开始会议」时发送）。
     *
     * @return 卡片 JSON 字符串
     */
    public String buildOnboardingInstructionCard() {
        ObjectNode card = objectMapper.createObjectNode();

        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);

        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "📖 智能会议 · 操作说明");
        header.put("template", "blue");

        ArrayNode elements = card.putArray("elements");
        addMarkdownElement(elements,
                "**快捷菜单**\n可使用「推送事件」菜单（订阅 **application.bot.menu_v6**，event_key 与后台一致）。"
                        + "系统将会话落到您**最近发过消息的群/单聊**，或**进入与机器人单聊**（建议订阅 **im.chat.access_event.bot_p2p_chat_entered_v1**）、"
                        + "或由**拉机器人进群**的操作者预关联；也可改用「向当前会话发送消息」并填写 **开始会议**。\n");
        addMarkdownElement(elements,
                "**开始会议**\n发送 **开始会议** 或点菜单后，在**打开的网页**上选择会议模板（含临时会议模板）；仍可用「开始会议 1」等文字指令。\n");
        addMarkdownElement(elements,
                "**会中与会后**\n• 会议主页点「结束会议」生成纪要（飞书「结束会议」仍为备用）\n"
                        + "• 查看纪要 / 查看纪要 最近10条\n"
                        + "• 注册声纹 姓名:…\n");
        addNoteElement(elements, List.of(
                "涉密高层会议建议线下；线上请用飞书发起并注意环境。",
                "本说明每个群仅自动推送一次；可随时输入「开始会议」继续使用。"
        ));

        return card.toString();
    }

    /**
     * 构建会议类型入口卡片：主按钮打开内嵌 Web（URL 中含短期 JWT），在页面上选类型。
     *
     * @param entryUrl 会务选会页面 URL
     * @return 卡片 JSON 字符串
     */
    public String buildMeetingTypeWebEntryCard(String entryUrl) {
        ObjectNode card = objectMapper.createObjectNode();

        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);

        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "选择会议类型");
        header.put("template", "blue");

        ArrayNode elements = card.putArray("elements");
        addMarkdownElement(elements,
                "请点击下方按钮，在**浏览器页面**中选择会务类型并创建会议（与飞书会话绑定，链接短时有效）。\n"
                        + "若无法打开，请检查后台 **meeting.base-url** 是否为公网 HTTPS。");
        addDividerElement(elements);
        addActionButton(elements, "打开会务选会页面", entryUrl, "primary");

        return card.toString();
    }

    /**
     * 构建会议前台统一入口卡片。
     */
    public String buildDashboardEntryCard(String userName, String dashboardUrl) {
        ObjectNode card = objectMapper.createObjectNode();
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);

        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "智能会议前台");
        header.put("template", "blue");

        ArrayNode elements = card.putArray("elements");
        String displayName = (userName == null || userName.isBlank()) ? "同事" : userName;
        addMarkdownElement(elements, "你好，" + displayName
                + "。点击下方按钮进入会议前台，可开始会议、注册声纹、查看纪要。");
        addDividerElement(elements);
        addActionButton(elements, "打开会议前台", dashboardUrl, "primary");
        return card.toString();
    }

    /**
     * 构建纪要完成卡片（含文档链接与待办统计）。
     *
     * @param title          会议主题
     * @param duration       会议时长（如「1小时15分钟」）
     * @param speakerCount   识别到的发言人数量
     * @param docUrl         飞书文档 URL
     * @param todoCount      待办数量
     * @param assigneeStats  责任人统计（如「张三(2项), 李四(1项)」）
     * @return 卡片 JSON 字符串
     */
    public String buildMinutesCard(String title, String duration, int speakerCount,
                                   String docUrl, int todoCount, String assigneeStats) {
        ObjectNode card = objectMapper.createObjectNode();
        
        // config
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);
        
        // header - 绿色
        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "✅ 会议纪要已生成");
        header.put("template", "green");
        
        // elements
        ArrayNode elements = card.putArray("elements");
        
        // 主题
        addMarkdownElement(elements, "**📋 主题**：" + title);
        
        // 时长
        addMarkdownElement(elements, "**⏱️ 会议时长**：" + duration);
        
        // 发言人
        addMarkdownElement(elements, "**👥 识别到 " + speakerCount + " 位发言人**");
        
        // 分割线
        addDividerElement(elements);
        
        // 文档按钮
        if (docUrl != null && !docUrl.isEmpty()) {
            addActionButton(elements, "📄 打开飞书文档", docUrl, "primary");
        }
        
        // 分割线
        addDividerElement(elements);
        
        // 待办统计
        addMarkdownElement(elements, "**📌 待办事项**：" + todoCount + "项（详见文档）");
        addMarkdownElement(elements, "**👤 责任人**：" + assigneeStats);
        
        return card.toString();
    }

    /**
     * 构建上次会议待办进度通报卡片。
     *
     * @param title        上次会议标题
     * @param completed    已完成数量
     * @param inProgress   进行中数量
     * @param delayed      已延期数量
     * @param delayedItems 延期项详情列表（含 content、assigneeName、blockReason）
     * @return 卡片 JSON 字符串
     */
    public String buildProgressCard(String title, int completed, int inProgress, int delayed,
                                    List<Map<String, String>> delayedItems) {
        ObjectNode card = objectMapper.createObjectNode();
        
        // config
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);
        
        // header - 黄色
        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "📊 上次会议待办进度");
        header.put("template", "yellow");
        
        // elements
        ArrayNode elements = card.putArray("elements");
        
        // 会议标题
        addMarkdownElement(elements, "**" + title + "**");
        
        // 统计
        String stats = String.format("- ✅ 已完成: %d\n- 🔄 进行中: %d\n- ⚠️ 已延期: %d", 
            completed, inProgress, delayed);
        addMarkdownElement(elements, stats);
        
        // 延期项详情
        if (delayed > 0 && delayedItems != null) {
            StringBuilder delayedText = new StringBuilder("**⚠️ 延期项详情:**\n\n");
            for (Map<String, String> item : delayedItems) {
                String content = item.getOrDefault("content", "");
                String assignee = item.getOrDefault("assigneeName", "未知");
                String reason = item.getOrDefault("blockReason", "");
                delayedText.append("- ").append(content)
                    .append("（责任人: ").append(assignee).append("）");
                if (!reason.isEmpty()) {
                    delayedText.append(" — ").append(reason);
                }
                delayedText.append("\n");
            }
            addMarkdownElement(elements, delayedText.toString());
        }
        
        return card.toString();
    }

    /**
     * 构建声纹注册引导卡片。
     *
     * @param userName 用户姓名
     * @param regUrl   声纹注册链接
     * @return 卡片 JSON 字符串
     */
    public String buildVoiceprintRegisterCard(String userName, String regUrl) {
        ObjectNode card = objectMapper.createObjectNode();
        
        // config
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);
        
        // header - 蓝色
        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "🎤 声纹注册");
        header.put("template", "blue");
        
        // elements
        ArrayNode elements = card.putArray("elements");
        
        // 用户信息
        addMarkdownElement(elements, "**注册姓名**：" + userName);
        addMarkdownElement(elements, "请在安静环境中完整朗读3句话（合计约200字）完成注册");
        
        // 分割线
        addDividerElement(elements);
        
        // 注册按钮
        addActionButton(elements, "🎤 点击开始注册", regUrl, "primary");
        
        // 分割线
        addDividerElement(elements);
        
        // 提示说明
        addNoteElement(elements, List.of(
            "💡 注册后系统可自动识别您的声音",
            "⏰ 注册链接30分钟内有效",
            "🔊 请确保录音时长≥5秒"
        ));
        
        return card.toString();
    }

    /**
     * 构建「纪要生成中」处理状态卡片。
     *
     * @param title 会议主题
     * @return 卡片 JSON 字符串
     */
    public String buildProcessingCard(String title) {
        ObjectNode card = objectMapper.createObjectNode();
        
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);
        
        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "⏳ 会议录音已结束");
        header.put("template", "blue");
        
        ArrayNode elements = card.putArray("elements");
        addMarkdownElement(elements, "正在生成纪要，预计 20-30 秒...");
        addMarkdownElement(elements, "**📋 主题**：" + title);
        
        return card.toString();
    }

    /**
     * 构建操作失败错误提示卡片。
     *
     * @param title    操作标题或上下文
     * @param errorMsg 错误信息
     * @return 卡片 JSON 字符串
     */
    public String buildErrorCard(String title, String errorMsg) {
        ObjectNode card = objectMapper.createObjectNode();
        
        ObjectNode config = card.putObject("config");
        config.put("wide_screen_mode", true);
        
        ObjectNode header = card.putObject("header");
        ObjectNode headerTitle = header.putObject("title");
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", "❌ 操作失败");
        header.put("template", "red");
        
        ArrayNode elements = card.putArray("elements");
        addMarkdownElement(elements, "**📋 " + title + "**");
        addMarkdownElement(elements, "错误信息：" + errorMsg);
        
        return card.toString();
    }

    // ==================== 辅助方法 ====================

    private void addMarkdownElement(ArrayNode elements, String content) {
        ObjectNode element = elements.addObject();
        element.put("tag", "div");
        ObjectNode text = element.putObject("text");
        text.put("tag", "lark_md");
        text.put("content", content);
    }

    private void addDividerElement(ArrayNode elements) {
        ObjectNode element = elements.addObject();
        element.put("tag", "hr");
    }

    private void addActionButton(ArrayNode elements, String buttonText, String url, String type) {
        ObjectNode actionWrapper = elements.addObject();
        actionWrapper.put("tag", "action");
        
        ArrayNode actions = actionWrapper.putArray("actions");
        ObjectNode button = actions.addObject();
        button.put("tag", "button");
        
        ObjectNode btnText = button.putObject("text");
        btnText.put("tag", "plain_text");
        btnText.put("content", buttonText);
        
        button.put("type", type);
        button.put("url", url);
    }

    /**
     * 回传按钮：勿设置 url。
     * 飞书新版要求通过 {@code behaviors} 声明 {@code type: callback} 及回传 {@code value}；仅根级 {@code value} 在新客户端上可能无法触发合法回调（客户端 200080）。
     */
    private void addCallbackButtonRow(ArrayNode elements, String label, ObjectNode value, String type) {
        ObjectNode actionWrapper = elements.addObject();
        actionWrapper.put("tag", "action");

        ArrayNode actions = actionWrapper.putArray("actions");
        ObjectNode button = actions.addObject();
        button.put("tag", "button");

        ObjectNode btnText = button.putObject("text");
        btnText.put("tag", "plain_text");
        btnText.put("content", label);

        button.put("type", type);
        ArrayNode behaviors = button.putArray("behaviors");
        ObjectNode callback = behaviors.addObject();
        callback.put("type", "callback");
        callback.set("value", value);
        // 历史字段：部分旧版客户端仍读取根级 value
        button.set("value", value);
    }

    private void addNoteElement(ArrayNode elements, List<String> notes) {
        ObjectNode note = elements.addObject();
        note.put("tag", "note");
        
        ArrayNode noteElements = note.putArray("elements");
        for (String text : notes) {
            ObjectNode noteItem = noteElements.addObject();
            noteItem.put("tag", "plain_text");
            noteItem.put("content", text);
        }
    }
}