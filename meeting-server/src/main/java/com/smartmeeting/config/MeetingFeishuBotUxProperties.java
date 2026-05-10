package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书机器人进群欢迎、菜单快捷、首次「开始会议」说明卡片等行为配置。
 * <p>
 * <b>可选方案（推送事件）</b>：在后台「机器人 → 自定义菜单」将项配置为「推送事件」，{@code event_key}
 * 与 {@link #menuEventKeyStartMeeting} 一致并订阅 {@code application.bot.menu_v6}。官方事件体常不带
 * {@code chat_id}，默认开启 {@link #menuV6FallbackToLastGroupChat}，用用户最近在群内发消息的会话作为目标群。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.feishu.bot-ux")
public class MeetingFeishuBotUxProperties {

    /** 机器人被拉入群时是否发送欢迎语 */
    private boolean welcomeOnBotJoin = true;

    /**
     * 进群欢迎纯文本（建议简短；详细操作可由配置 {@link #instructionCardOnFirstStart} 的说明卡片补充，默认关闭）
     */
    private String welcomeText = "大家好，我是「智能会议纪要」助手。\n"
            + "请使用机器人下方「快捷菜单」发送「开始会议」，或直接在群内输入「开始会议」即可。";

    /** 用户在本群首次发送「开始会议」（仅菜单入口、不含数字）时是否先发一张常驻说明卡片；默认 false，避免与类型选择卡片叠放 */
    private boolean instructionCardOnFirstStart = false;

    /**
     * 与开发者后台「推送事件」类菜单项配置的 event_key 一致时，视为用户点击「开始会议」快捷指令。
     * 若菜单配置为「发送消息：开始会议」，则不会走此事件，由 im.message.receive_v1 处理即可。
     */
    private String menuEventKeyStartMeeting = "start_meeting";

    /**
     * 当菜单推送事件无 chat_id 时，是否使用「该用户最近在群内发过消息的 chat_id」作为回退（推荐开启）。
     */
    private boolean menuV6FallbackToLastGroupChat = true;
}
