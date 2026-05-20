package com.smartmeeting.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册配置。
 * <p>
 * 注册音频推流与 AI 主持态广播两条 WebSocket 路径。
 *
 * @see AudioWebSocketHandler
 * @see MeetingHostWebSocketHandler
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AudioWebSocketHandler audioWebSocketHandler;
    private final MeetingHostWebSocketHandler meetingHostWebSocketHandler;

    /**
     * @param audioWebSocketHandler      PCM 音频与 ASR 桥接处理器
     * @param meetingHostWebSocketHandler 主持页状态广播处理器
     */
    public WebSocketConfig(AudioWebSocketHandler audioWebSocketHandler,
                           MeetingHostWebSocketHandler meetingHostWebSocketHandler) {
        this.audioWebSocketHandler = audioWebSocketHandler;
        this.meetingHostWebSocketHandler = meetingHostWebSocketHandler;
    }

    /**
     * 注册 {@code /ws/audio/{meetingId}} 与 {@code /ws/host/{meetingId}} 处理器。
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler, "/ws/audio/{meetingId}")
                .setAllowedOrigins("*");
        registry.addHandler(meetingHostWebSocketHandler, "/ws/host/{meetingId}")
                .setAllowedOrigins("*");
    }
}
