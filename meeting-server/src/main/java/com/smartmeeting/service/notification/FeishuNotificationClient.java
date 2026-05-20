package com.smartmeeting.service.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartmeeting.config.MeetingNotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * feishu-scheduled-bot 推送 HTTP 客户端：向 Bot 的 {@code /api/push} 接口发送卡片或文本消息。
 * <p>
 * 主要协作组件：{@link RestTemplate}、{@link MeetingNotificationProperties}（Bot 地址与 API Key）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuNotificationClient {

    private final RestTemplate restTemplate;
    private final MeetingNotificationProperties properties;

    /**
     * 向 Bot 推送飞书卡片消息。
     *
     * @param eventType       事件类型
     * @param meetingId       关联会议 ID
     * @param idempotencyKey  幂等键
     * @param targetType      目标类型（GROUP / USER）
     * @param targetId        目标 ID（chat_id 或 open_id）
     * @param title           卡片标题
     * @param elements        卡片元素（content、可选 doc_url）
     * @return Bot 返回 SUCCESS 或 SKIPPED 时为 {@code true}；否则 {@code false}
     * @throws RestClientException HTTP 请求失败时抛出
     */
    public boolean pushCard(String eventType,
                            String meetingId,
                            String idempotencyKey,
                            String targetType,
                            String targetId,
                            String title,
                            List<Map<String, String>> elements) {
        String url = properties.getBotBaseUrl().replaceAll("/$", "") + "/api/push";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", properties.getBotApiKey());

        List<CardElementDto> cardElements = elements.stream()
                .map(e -> new CardElementDto(
                        e.getOrDefault("content", ""),
                        e.get("doc_url")))
                .toList();

        EventPushBody body = new EventPushBody(
                "smart-meeting",
                eventType,
                meetingId,
                idempotencyKey,
                targetType,
                targetId,
                "card",
                null,
                new CardPayloadDto(title, "blue", cardElements)
        );

        try {
            ResponseEntity<EventPushResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    EventPushResponse.class
            );
            EventPushResponse resp = response.getBody();
            if (resp == null) {
                log.warn("Bot push empty response: eventType={}", eventType);
                return false;
            }
            if ("SUCCESS".equals(resp.status()) || "SKIPPED".equals(resp.status())) {
                return true;
            }
            log.warn("Bot push failed: eventType={}, status={}, error={}",
                    eventType, resp.status(), resp.errorMessage());
            return false;
        } catch (RestClientException e) {
            log.warn("Bot push HTTP error: eventType={}, {}", eventType, e.getMessage());
            throw e;
        }
    }

    /**
     * 向 Bot 推送飞书纯文本消息。
     *
     * @param eventType       事件类型
     * @param meetingId       关联会议 ID
     * @param idempotencyKey  幂等键
     * @param targetType      目标类型（GROUP / USER）
     * @param targetId        目标 ID
     * @param text            消息正文
     * @return Bot 返回 SUCCESS 或 SKIPPED 时为 {@code true}；否则 {@code false}
     */
    public boolean pushText(String eventType,
                            String meetingId,
                            String idempotencyKey,
                            String targetType,
                            String targetId,
                            String text) {
        String url = properties.getBotBaseUrl().replaceAll("/$", "") + "/api/push";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", properties.getBotApiKey());

        EventPushBody body = new EventPushBody(
                "smart-meeting",
                eventType,
                meetingId,
                idempotencyKey,
                targetType,
                targetId,
                "text",
                text,
                null
        );

        ResponseEntity<EventPushResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), EventPushResponse.class);
        EventPushResponse resp = response.getBody();
        return resp != null && ("SUCCESS".equals(resp.status()) || "SKIPPED".equals(resp.status()));
    }

    /**
     * Bot 推送请求体。
     *
     * @param source          来源系统标识（如 smart-meeting）
     * @param eventType       事件类型
     * @param meetingId       关联会议 ID
     * @param idempotencyKey  幂等键
     * @param targetType      目标类型（GROUP / USER）
     * @param targetId        目标 ID
     * @param msgType         消息类型（card / text）
     * @param text            文本消息正文（msgType=text 时使用）
     * @param card            卡片载荷（msgType=card 时使用）
     */
    record EventPushBody(
            String source,
            String eventType,
            String meetingId,
            String idempotencyKey,
            String targetType,
            String targetId,
            String msgType,
            String text,
            CardPayloadDto card
    ) {}

    /**
     * 卡片消息载荷。
     *
     * @param title     卡片标题
     * @param template  卡片模板色（如 blue）
     * @param elements  卡片正文元素列表
     */
    record CardPayloadDto(String title, String template, List<CardElementDto> elements) {}

    /**
     * 卡片单个正文元素。
     *
     * @param content Markdown 正文内容
     * @param docUrl  可选文档链接（用于按钮）
     */
    record CardElementDto(String content, String docUrl) {}

    /**
     * Bot 推送响应。
     *
     * @param pushLogId       推送日志 ID
     * @param status          状态（SUCCESS / SKIPPED / FAILED 等）
     * @param skipReason      跳过原因（status=SKIPPED 时）
     * @param feishuMessageId 飞书消息 ID（成功时）
     * @param errorMessage    错误信息（失败时）
     * @param responseCode    响应码
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventPushResponse(
            String pushLogId,
            String status,
            String skipReason,
            String feishuMessageId,
            String errorMessage,
            String responseCode
    ) {}
}
