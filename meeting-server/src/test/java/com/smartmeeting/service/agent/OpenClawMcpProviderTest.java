package com.smartmeeting.service.agent;

import com.smartmeeting.config.OpenClawProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenClaw MCP 单元测试：配置与 {@code application-dev.yml} 对齐。
 *
 * <p>联机验证见 {@link OpenClawGatewayWsClientLiveTest}。
 */
@ExtendWith(MockitoExtension.class)
class OpenClawMcpProviderTest {

    private static OpenClawDevConfigLoader.DevOpenClawConfig devConfig;

    /** 模拟本机 dev 启动时 export OPENCLAW_AUTH_TOKEN=... */
    private static final String DEV_AUTH_TOKEN = "dev-unit-test-auth-token";

    @Mock
    private OpenClawGatewayWsClient gatewayWsClient;

    private OpenClawMcpProvider provider;

    @BeforeEach
    void setUpProvider() {
        if (devConfig == null) {
            devConfig = OpenClawDevConfigLoader.load();
        }
        provider = new OpenClawMcpProvider(gatewayWsClient, devOpenClawProperties());
        provider.initInvokeSemaphore();
        applyDevDefaults(provider);
    }

    private static OpenClawProperties devOpenClawProperties() {
        OpenClawProperties props = new OpenClawProperties();
        props.setEnabled(devConfig.enabled());
        props.setSkillMode(devConfig.skillMode());
        props.setTimeoutSeconds(devConfig.timeoutSeconds());
        props.setMaxConcurrentInvokes(5);
        props.getAgent().setProvider(devConfig.provider());
        return props;
    }

    private static void applyDevDefaults(OpenClawMcpProvider target) {
        ReflectionTestUtils.setField(target, "gatewayUrl", devConfig.gatewayUrl());
        ReflectionTestUtils.setField(target, "sessionKey", devConfig.sessionKey());
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

        String raw = "# 初版纪要\n\n内容";
        assertThat(provider.enhanceMeetingMinutes("m-dev-1", raw, "综合管理会", 1, "张三", null))
                .isEqualTo(raw);
        verify(gatewayWsClient, never()).sendChatMessage(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("dev + OPENCLAW_AUTH_TOKEN：Skill 模式纪要增强走 Gateway timeout=120")
    void devWithAuthToken_skillMode_sendsMinuteEnhancementToDevGateway() {
        applyDevWithAuthToken(provider);
        String raw = "# 初版纪要\n\n待优化内容";

        when(gatewayWsClient.sendChatMessage(
                eq(devConfig.gatewayUrl()), eq(DEV_AUTH_TOKEN), eq(""),
                any(), any(), eq(devConfig.timeoutSeconds()), any()))
                .thenReturn("{\"optimized_minute\":\"优化后纪要\",\"quality_check\":{\"score\":90}}");

        String result = provider.enhanceMeetingMinutes(
                "m-dev-2", raw, "综合管理会", 1, "张三,李四", "转写片段");

        assertThat(provider.isAvailable()).isTrue();
        assertThat(result).contains("optimized_minute");
    }

    @Test
    @DisplayName("纪要增强 sessionKey 按 taskId 隔离")
    void resolveSessionKeyForTask_isolatesPerTaskId() {
        String task1 = OpenClawTaskIds.minuteEnhancement("m1", 1L);
        String task2 = OpenClawTaskIds.minuteEnhancement("m1", 2L);
        String k1 = OpenClawMcpProvider.resolveSessionKeyForTask(task1, "agent:base");
        String k2 = OpenClawMcpProvider.resolveSessionKeyForTask(task2, "agent:base");
        assertThat(k1).contains(":task:minute_enhancement:m1:1");
        assertThat(k2).contains(":task:minute_enhancement:m1:2");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    @DisplayName("dev OPENCLAW_ENABLED=false 时不调 Gateway")
    void devOpenclawDisabled_skipsGateway() {
        applyDevWithAuthToken(provider);
        ReflectionTestUtils.setField(provider, "openClawProperties", disabledProps());
        String raw = "raw minute";

        assertThat(provider.isAvailable()).isFalse();
        assertThat(provider.enhanceMeetingMinutes("m-dev-4", raw, "会", 1, "张三", null))
                .isEqualTo(raw);
        verify(gatewayWsClient, never()).sendChatMessage(any(), any(), any(), any(), any(), anyInt());
    }

    private static OpenClawProperties disabledProps() {
        OpenClawProperties props = devOpenClawProperties();
        props.setEnabled(false);
        return props;
    }
}
