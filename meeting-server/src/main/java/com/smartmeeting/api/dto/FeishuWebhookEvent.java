package com.smartmeeting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 飞书 Webhook 事件 JSON 映射体（schema 2.0 包裹结构）。
 * <p>
 * 用于可选的反序列化场景；主流程 {@link com.smartmeeting.api.controller.FeishuWebhookController}
 * 使用 {@code JsonNode} 灵活解析。含嵌套 {@link Header}、{@link Event} 及发送者/会话子结构。
 */
@Data
public class FeishuWebhookEvent {
    private String schema;
    private Header header;
    private Event event;

    /**
     * 飞书事件头：事件类型、ID、校验 token 等。
     */
    @Data
    public static class Header {
        @JsonProperty("event_id")
        private String eventId;
        @JsonProperty("event_type")
        private String eventType;
        @JsonProperty("create_time")
        private String createTime;
        private String token;
        @JsonProperty("app_id")
        private String appId;
        private String tenantKey;
    }

    /**
     * 事件载荷：消息内容、发送者、会话等（字段随 event_type 变化）。
     */
    @Data
    public static class Event {
        private String text;
        private String messageType;
        private Sender sender;
        private MessageChat chat;
        private String appId;

        /**
         * 消息发送者信息。
         */
        @Data
        public static class Sender {
            @JsonProperty("sender_id")
            private SenderId senderId;
            @JsonProperty("sender_type")
            private String senderType;

            /**
             * 飞书用户 ID 三元组。
             */
            @Data
            public static class SenderId {
                @JsonProperty("open_id")
                private String openId;
                @JsonProperty("user_id")
                private String userId;
                @JsonProperty("union_id")
                private String unionId;
            }
        }

        /**
         * 消息所在会话（群/单聊）。
         */
        @Data
        public static class MessageChat {
            @JsonProperty("chat_id")
            private String chatId;
            @JsonProperty("chat_mode")
            private String chatMode;
            private String name;
        }
    }
}
