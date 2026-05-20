package com.smartmeeting.service.notification;

import com.smartmeeting.config.MeetingNotificationProperties;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.host.MeetingHostFeishuMuteRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * 会后等业务飞书通知门面：优先经 feishu-scheduled-bot 推送，失败时可回退直连 {@link FeishuService}。
 * <p>
 * 主要协作组件：{@link FeishuNotificationClient}、{@link MeetingNotificationProperties}、
 * {@link MeetingHostFeishuMuteRegistry}（AI 主持期间抑制推送）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingFeishuNotifier {

    private final FeishuService feishuService;
    private final FeishuNotificationClient notificationClient;
    private final MeetingNotificationProperties notificationProperties;
    private final MeetingHostFeishuMuteRegistry muteRegistry;

    /**
     * 发送飞书卡片消息（经 Bot 或直连降级）。
     *
     * @param targetId        接收方 ID（群 chat_id 以 oc_ 开头，否则视为用户 open_id）
     * @param title           卡片标题
     * @param elements        卡片正文元素列表（含 content、可选 doc_url）
     * @param eventType       事件类型（如 TODO_SYNC、MINUTE_READY）
     * @param meetingId       关联会议 ID
     * @param idempotencyKey  幂等键，防止重复推送
     * @return 发送成功返回 {@code true}；目标为空、被静音或全部渠道失败时返回 {@code false}
     */
    public boolean sendCardMessage(String targetId,
                                   String title,
                                   List<Map<String, String>> elements,
                                   String eventType,
                                   String meetingId,
                                   String idempotencyKey) {
        if (targetId == null || targetId.isBlank()) {
            log.warn("Skip notification: empty targetId, eventType={}", eventType);
            return false;
        }
        if (muteRegistry.isMuted(targetId)) {
            log.warn("Feishu send suppressed (AI host in-session): eventType={}, targetId={}",
                    eventType, targetId);
            return false;
        }

        if (notificationProperties.isBotEnabled()) {
            try {
                boolean ok = notificationClient.pushCard(
                        eventType,
                        meetingId,
                        idempotencyKey,
                        resolveTargetType(targetId),
                        targetId,
                        title,
                        elements);
                if (ok) {
                    return true;
                }
            } catch (RestClientException e) {
                log.warn("Bot notification failed, eventType={}: {}", eventType, e.getMessage());
            }
            if (!notificationProperties.isFallbackDirect()) {
                return false;
            }
            log.info("Falling back to direct Feishu send: eventType={}", eventType);
        }

        return feishuService.sendCardMessage(targetId, title, elements);
    }

    /**
     * 发送飞书纯文本消息（经 Bot 或直连降级）。
     *
     * @param targetId        接收方 ID
     * @param text            消息正文
     * @param eventType       事件类型
     * @param meetingId       关联会议 ID
     * @param idempotencyKey  幂等键
     * @return 发送成功返回 {@code true}；目标为空、被静音或全部渠道失败时返回 {@code false}
     */
    public boolean sendTextMessage(String targetId,
                                   String text,
                                   String eventType,
                                   String meetingId,
                                   String idempotencyKey) {
        if (targetId == null || targetId.isBlank()) {
            return false;
        }
        if (muteRegistry.isMuted(targetId)) {
            return false;
        }

        if (notificationProperties.isBotEnabled()) {
            try {
                boolean ok = notificationClient.pushText(
                        eventType, meetingId, idempotencyKey,
                        resolveTargetType(targetId), targetId, text);
                if (ok) {
                    return true;
                }
            } catch (RestClientException e) {
                log.warn("Bot text push failed: {}", e.getMessage());
            }
            if (!notificationProperties.isFallbackDirect()) {
                return false;
            }
        }
        return feishuService.sendMessage(targetId, text);
    }

    /**
     * 根据 targetId 前缀推断推送目标类型。
     *
     * @param targetId 接收方 ID
     * @return {@code "GROUP"}（oc_ 前缀）或 {@code "USER"}
     */
    static String resolveTargetType(String targetId) {
        if (targetId.startsWith("oc_")) {
            return "GROUP";
        }
        return "USER";
    }
}
