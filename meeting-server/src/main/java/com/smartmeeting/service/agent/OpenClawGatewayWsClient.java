package com.smartmeeting.service.agent;

import com.smartmeeting.config.OpenClawProperties;
import com.smartmeeting.matterprogress.openclaw.OpenClawGatewayHistoryFetchSettings;
import org.springframework.stereotype.Component;

/**
 * meeting-server 对 OpenClaw WS 客户端的薄委托层。
 *
 * <p>核心逻辑已迁至 {@link com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient}；
 * 本类保留 Spring {@code @Component} 以兼容现有注入点。
 */
@Component
public class OpenClawGatewayWsClient {

    private final com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient delegate;

    public OpenClawGatewayWsClient(OpenClawProperties openClawProperties) {
        OpenClawProperties.Gateway gw = openClawProperties.getGateway();
        OpenClawGatewayHistoryFetchSettings settings = new OpenClawGatewayHistoryFetchSettings(
                gw.getHistoryFetchAttempts(),
                gw.getHistoryFetchDelayMs(),
                gw.getHistoryFetchExtendedAttempts(),
                gw.getHistoryFetchExtendedDelayMs());
        this.delegate = new com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient(settings);
    }

    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds) {
        return delegate.sendChatMessage(gatewayHttpUrl, authToken, deviceToken, sessionKey, message, timeoutSeconds);
    }

    public String sendChatMessage(String gatewayHttpUrl,
                                  String authToken,
                                  String deviceToken,
                                  String sessionKey,
                                  String message,
                                  int timeoutSeconds,
                                  String taskId) {
        return delegate.sendChatMessage(gatewayHttpUrl, authToken, deviceToken, sessionKey, message, timeoutSeconds, taskId);
    }

    public boolean pingHttp(String gatewayHttpUrl) {
        return delegate.pingHttp(gatewayHttpUrl);
    }

    public static boolean isLoopbackGateway(String gatewayHttpUrl) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient.isLoopbackGateway(gatewayHttpUrl);
    }

    public static String toWebSocketUrl(String gatewayHttpUrl) {
        return com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient.toWebSocketUrl(gatewayHttpUrl);
    }
}
