package com.smartmeeting.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class FeishuWebhookEvent {
    private String schema;
    private Header header;
    private Event event;

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

    @Data
    public static class Event {
        private String text;
        private String messageType;
        private Sender sender;
        private MessageChat chat;
        private String appId;

        @Data
        public static class Sender {
            @JsonProperty("sender_id")
            private SenderId senderId;
            @JsonProperty("sender_type")
            private String senderType;

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
