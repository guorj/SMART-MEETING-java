package com.smartmeeting.service.agent;

import com.smartmeeting.config.OpenClawProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对阿里云 OpenClaw Gateway 的联机测试（需网络 + {@code OPENCLAW_AUTH_TOKEN}）。
 */
class OpenClawGatewayWsClientLiveTest {

    private static final String GATEWAY = System.getenv().getOrDefault(
            "OPENCLAW_GATEWAY_URL", "http://39.97.61.212:18789");
    private static final String TOKEN = System.getenv("OPENCLAW_AUTH_TOKEN");
    private static final String DEVICE_TOKEN = System.getenv("OPENCLAW_DEVICE_TOKEN");
    private static final String SESSION_KEY = System.getenv().getOrDefault(
            "OPENCLAW_SESSION_KEY",
            "agent:jqclaw:direct:ou_5eadac907bd2dc79e8b39205ef6bf33a");

    @Test
    void healthPing() {
        OpenClawGatewayWsClient client = new OpenClawGatewayWsClient(new OpenClawProperties());
        assertTrue(client.pingHttp(GATEWAY), "Gateway /health should return ok");
    }

    @Test
    void chatSendMatterProgressSkill() {
        boolean hasAuth = (TOKEN != null && !TOKEN.isBlank())
                || (DEVICE_TOKEN != null && !DEVICE_TOKEN.isBlank());
        Assumptions.assumeTrue(hasAuth, "Set OPENCLAW_AUTH_TOKEN or OPENCLAW_DEVICE_TOKEN");
        Assumptions.assumeTrue(
                OpenClawGatewayWsClient.isLoopbackGateway(GATEWAY)
                        || (DEVICE_TOKEN != null && !DEVICE_TOKEN.isBlank()),
                "Remote shared-token WS has no operator.write; use OPENCLAW_GATEWAY_URL=http://127.0.0.1:18789 on Gateway host, or OPENCLAW_DEVICE_TOKEN");

        OpenClawGatewayWsClient client = new OpenClawGatewayWsClient(new OpenClawProperties());
        String prompt = """
                /skill:matter-progress
                meetingId=test-meeting-001
                title=联调测试会议
                company=测试集团
                groupName=综合管理会
                """;

        String reply = client.sendChatMessage(GATEWAY, TOKEN, DEVICE_TOKEN, SESSION_KEY, prompt, 120);
        assertNotNull(reply, "Expected non-null assistant reply from Gateway");
        assertTrue(reply.length() > 20, "Reply too short: " + reply);
        System.out.println("=== OpenClaw reply (first 800 chars) ===");
        System.out.println(reply.length() > 800 ? reply.substring(0, 800) + "..." : reply);
    }
}
