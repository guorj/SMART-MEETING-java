package com.smartmeeting.service.agent;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.service.AiAgentService;
import com.smartmeeting.service.BitableDirectiveBuilder;
import com.smartmeeting.service.FeishuService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.service.feishu.FeishuResourceRef;
import com.smartmeeting.service.host.AgendaBriefingResult;
import com.smartmeeting.service.host.AgendaBriefingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenClaw MCP 单元测试：配置与 {@code application-dev.yml} 对齐。
 *
 * <p>dev 默认：{@code provider=mcp}、{@code gateway-url=http://127.0.0.1:18792}、
 * {@code timeout-seconds=120}、{@code skill-mode=true}；{@code auth-token}/{@code device-token}
 * 默认为空，故 {@link OpenClawMcpProvider#isAvailable()} 为 false，须模拟设置
 * {@code OPENCLAW_AUTH_TOKEN} 后才可走 Gateway。
 *
 * <p>联机验证见 {@link OpenClawGatewayWsClientLiveTest}。
 */
@ExtendWith(MockitoExtension.class)
class OpenClawMcpProviderTest {

    private static OpenClawDevConfigLoader.DevOpenClawConfig devConfig;

    /** 模拟本机 dev 启动时 export OPENCLAW_AUTH_TOKEN=... */
    private static final String DEV_AUTH_TOKEN = "dev-unit-test-auth-token";

    private static final String FEISHU_URL =
            "https://ovjde0k7vc1.feishu.cn/base/abc?table=tbl1&view=vew1";

    @Mock
    private OpenClawGatewayWsClient gatewayWsClient;
    @Mock
    private MeetingMapper meetingMapper;
    @Mock
    private BitableDirectiveBuilder bitableDirectiveBuilder;
    @Mock
    private FeishuService feishuService;
    @Mock
    private RestTemplate restTemplate;

    private OpenClawMcpProvider provider;

    @BeforeEach
    void setUpProvider() {
        if (devConfig == null) {
            devConfig = OpenClawDevConfigLoader.load();
        }
        provider = new OpenClawMcpProvider(gatewayWsClient);
        applyDevDefaults(provider);
    }

    private static void applyDevDefaults(OpenClawMcpProvider target) {
        ReflectionTestUtils.setField(target, "enabled", devConfig.enabled());
        ReflectionTestUtils.setField(target, "gatewayUrl", devConfig.gatewayUrl());
        ReflectionTestUtils.setField(target, "sessionKey", devConfig.sessionKey());
        ReflectionTestUtils.setField(target, "timeoutSeconds", devConfig.timeoutSeconds());
        ReflectionTestUtils.setField(target, "agendaBriefingTimeoutSeconds", devConfig.timeoutSeconds());
        ReflectionTestUtils.setField(target, "skillMode", devConfig.skillMode());
        ReflectionTestUtils.setField(target, "authToken", "");
        ReflectionTestUtils.setField(target, "deviceToken", "");
    }

    private static void applyDevWithAuthToken(OpenClawMcpProvider target) {
        applyDevDefaults(target);
        ReflectionTestUtils.setField(target, "authToken", DEV_AUTH_TOKEN);
    }

    @Test
    @DisplayName("dev 默认：无 OPENCLAW_AUTH_TOKEN/DEVICE_TOKEN 时 isAvailable=false")
    void devDefaults_withoutAuth_isNotAvailable() {
        assertThat(devConfig.enabled()).isTrue();
        assertThat(devConfig.provider()).isEqualTo("mcp");
        assertThat(devConfig.gatewayUrl()).isEqualTo("http://127.0.0.1:18789");
        assertThat(devConfig.timeoutSeconds()).isEqualTo(120);
        assertThat(devConfig.skillMode()).isTrue();

        assertThat(provider.isAvailable()).isFalse();

        Meeting meeting = meeting("m-dev-1", "综合管理会", "XX集团", "综合管理会");
        assertThat(provider.runMatterProgressReport(meeting, "directive", FEISHU_URL, "会序1")).isNull();
        verify(gatewayWsClient, never()).sendChatMessage(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("dev + OPENCLAW_AUTH_TOKEN：Skill 模式会序通报走 Gateway(18792) timeout=120")
    void devWithAuthToken_skillMode_sendsMatterProgressToDevGateway() {
        applyDevWithAuthToken(provider);
        Meeting meeting = meeting("m-dev-2", "综合管理会", "XX集团", "综合管理会");
        String directive = "【飞书资料-主持会序通报】table=tbl1";

        when(gatewayWsClient.sendChatMessage(
                eq(devConfig.gatewayUrl()), eq(DEV_AUTH_TOKEN), eq(""), eq(devConfig.sessionKey()),
                argThat(prompt -> prompt.startsWith("/skill:matter-progress")
                        && prompt.contains("meetingId=m-dev-2")
                        && prompt.contains("feishuUrl=" + FEISHU_URL)
                        && prompt.contains("agendaTitle=会序2")),
                eq(devConfig.timeoutSeconds())))
                .thenReturn("{\"reply\":\"# 会序通报\\n\\n进度正常\"}");

        String markdown = provider.runMatterProgressReport(
                meeting, directive, FEISHU_URL, "会序2");

        assertThat(provider.isAvailable()).isTrue();
        assertThat(markdown).contains("会序通报");
    }

    @Test
    @DisplayName("dev + OPENCLAW_AUTH_TOKEN：Gateway payloads 格式解析")
    void devWithAuthToken_parsesPayloadsFormat() {
        applyDevWithAuthToken(provider);
        Meeting meeting = meeting("m-dev-3", "技术委员会", "集团A", "技委会");
        String payloadsJson = """
                {"status":"ok","result":{"payloads":[{"text":"# 通报\\n\\n分项进度良好"}]}}
                """;
        when(gatewayWsClient.sendChatMessage(
                eq(devConfig.gatewayUrl()), eq(DEV_AUTH_TOKEN), eq(""), eq(devConfig.sessionKey()),
                any(), eq(devConfig.timeoutSeconds())))
                .thenReturn(payloadsJson);

        String markdown = provider.runMatterProgressReport(
                meeting, "directive", FEISHU_URL, "议题A");

        assertThat(markdown).contains("分项进度良好");
    }

    @Test
    @DisplayName("dev OPENCLAW_ENABLED=false 时不调 Gateway")
    void devOpenclawDisabled_skipsGateway() {
        applyDevWithAuthToken(provider);
        ReflectionTestUtils.setField(provider, "enabled", false);
        Meeting meeting = meeting("m-dev-4", "会", "C", "G");

        assertThat(provider.isAvailable()).isFalse();
        assertThat(provider.runMatterProgressReport(meeting, "d", FEISHU_URL, "会序1")).isNull();
        verify(gatewayWsClient, never()).sendChatMessage(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("dev + OPENCLAW_AUTH_TOKEN：AgendaBriefingService 返回 openclaw_gateway")
    void devWithAuthToken_agendaBriefing_returnsOpenclawGateway() {
        applyDevWithAuthToken(provider);
        Meeting meeting = meeting("m-brief-dev", "综合管理会", "XX集团", "综合管理会");
        when(meetingMapper.selectById("m-brief-dev")).thenReturn(meeting);
        when(bitableDirectiveBuilder.buildDirectiveForHostAgenda("会序2", FEISHU_URL, "BASE"))
                .thenReturn("【主持会序】directive");
        when(gatewayWsClient.sendChatMessage(
                eq(devConfig.gatewayUrl()), eq(DEV_AUTH_TOKEN), eq(""), eq(devConfig.sessionKey()),
                argThat(p -> p.contains("/skill:matter-progress") && p.contains("agendaTitle=会序2")),
                eq(devConfig.timeoutSeconds())))
                .thenReturn("{\"reply\":\"# MCP 会序通报\\n\\n- 概览：正常\"}");

        AiAgentService aiAgentService = new AiAgentService(provider);
        AgendaBriefingService briefingService = new AgendaBriefingService(
                meetingMapper, aiAgentService, bitableDirectiveBuilder);
        ReflectionTestUtils.setField(briefingService, "enabled", true);

        AgendaBriefingResult result = briefingService.generateBriefing(
                "m-brief-dev", "会序2", FEISHU_URL, "BASE");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSource()).isEqualTo("openclaw_gateway");
        assertThat(result.getMarkdown()).contains("MCP 会序通报");
    }

    @Test
    @DisplayName("dev 默认无 Token：AgendaBriefing 失败，不兜底 LLM")
    void devDefaultsWithoutToken_agendaBriefing_failsWithoutGateway() {
        Meeting meeting = meeting("m-brief-fallback", "综合管理会", "XX集团", "综合管理会");
        when(meetingMapper.selectById("m-brief-fallback")).thenReturn(meeting);
        when(bitableDirectiveBuilder.buildDirectiveForHostAgenda("会序2", FEISHU_URL, "BASE"))
                .thenReturn("【主持会序】directive");

        AiAgentService aiAgentService = new AiAgentService(provider);
        AgendaBriefingService briefingService = new AgendaBriefingService(
                meetingMapper, aiAgentService, bitableDirectiveBuilder);
        ReflectionTestUtils.setField(briefingService, "enabled", true);

        AgendaBriefingResult result = briefingService.generateBriefing(
                "m-brief-fallback", "会序2", FEISHU_URL, "BASE");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("OpenClaw");
        verify(gatewayWsClient, never()).sendChatMessage(any(), any(), any(), any(), any(), anyInt());
    }

    private static Meeting meeting(String id, String title, String company, String groupName) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setTitle(title);
        m.setCompany(company);
        m.setGroupName(groupName);
        return m;
    }
}
