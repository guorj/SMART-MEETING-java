package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpenClawGatewayWsClient} 协议辅助逻辑（不连真实 Gateway）。
 */
class OpenClawGatewayWsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("chat 流结束：done=true 或 state=final")
    void isChatStreamComplete_doneOrFinal() throws Exception {
        assertThat(objectMapper.readTree("{\"done\":true}").path("done").asBoolean()).isTrue();
        assertThat(objectMapper.readTree("{\"state\":\"final\"}").path("state").asText()).isEqualTo("final");
    }

    @Test
    @DisplayName("loopback Gateway 识别")
    void isLoopbackGateway_localhost() {
        assertThat(OpenClawGatewayWsClient.isLoopbackGateway("http://127.0.0.1:18789")).isTrue();
        assertThat(OpenClawGatewayWsClient.isLoopbackGateway("http://localhost:18789")).isTrue();
        assertThat(OpenClawGatewayWsClient.isLoopbackGateway("http://39.97.61.212:18789")).isFalse();
    }

    @Test
    @DisplayName("HTTP Gateway URL 转 WebSocket")
    void toWebSocketUrl() {
        assertThat(OpenClawGatewayWsClient.toWebSocketUrl("http://127.0.0.1:18789"))
                .isEqualTo("ws://127.0.0.1:18789");
        assertThat(OpenClawGatewayWsClient.toWebSocketUrl("https://example.com:18789"))
                .isEqualTo("wss://example.com:18789");
    }
}
