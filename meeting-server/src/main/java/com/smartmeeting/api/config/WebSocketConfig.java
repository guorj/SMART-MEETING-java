package com.smartmeeting.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final MeetingHostWebSocketHandler meetingHostWebSocketHandler;

    public WebSocketConfig(AudioWebSocketHandler audioWebSocketHandler,
                           MeetingHostWebSocketHandler meetingHostWebSocketHandler) {
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.meetingHostWebSocketHandler = meetingHostWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler, "/ws/audio/{meetingId}")
                .setAllowedOrigins("*");
        registry.addHandler(meetingHostWebSocketHandler, "/ws/host/{meetingId}")
                .setAllowedOrigins("*");
    }
}
