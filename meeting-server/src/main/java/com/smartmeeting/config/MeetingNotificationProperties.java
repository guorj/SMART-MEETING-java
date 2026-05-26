package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会后通知推送配置，绑定 {@code meeting.notification.*} 前缀。
 * <p>
 * 控制是否通过 feishu-scheduled-bot 中转发送通知，以及失败时的回退策略。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.notification")
public class MeetingNotificationProperties {

    /** 是否通过 feishu-scheduled-bot POST /api/push 发送会后通知 */
    private boolean botEnabled = false;

    /** feishu-scheduled-bot 服务基地址 */
    private String botBaseUrl = "http://127.0.0.1:8764";

    /** 调用 bot 接口时使用的 API Key（{@code meeting.notification.scheduled-bot-apikey}） */
    private String scheduledBotApikey = "";

    /** Bot 调用失败时是否回退至 FeishuService 直连飞书 API */
    private boolean fallbackDirect = true;
}
