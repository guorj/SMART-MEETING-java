package com.smartmeeting.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MatterProgressFetchProperties;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.host.MeetingHostFeishuMuteRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书探针工厂：从 {@code application-dev.yml} 读取凭据并构造 {@link FeishuService}，免启动 Spring 容器。
 */
final class FeishuProbeFactory {

    /** 解析 dev 配置并返回可调用飞书 Open API 的服务实例。 */
    static FeishuService createFeishuService() throws Exception {
        Path yml = Paths.get("src/main/resources/application-dev.yml");
        if (!Files.isRegularFile(yml)) {
            yml = Paths.get("meeting-server/src/main/resources/application-dev.yml");
        }
        String text = Files.readString(yml, StandardCharsets.UTF_8);
        int feishuStart = text.indexOf("\n  feishu:");
        if (feishuStart < 0) {
            feishuStart = text.indexOf("feishu:");
        }
        String feishuBlock = feishuStart >= 0 ? text.substring(feishuStart) : text;
        String appId = yamlValue(feishuBlock, "app-id");
        String appSecret = yamlValue(feishuBlock, "app-secret");
        String baseUrl = yamlValue(feishuBlock, "base-url");
        if (appId == null || appSecret == null) {
            throw new IllegalStateException("application-dev.yml 缺少 meeting.feishu.app-id/app-secret");
        }
        FeishuService svc = new FeishuService(new RestTemplate(), new ObjectMapper(),
                new MeetingHostFeishuMuteRegistry(), new MatterProgressFetchProperties());
        ReflectionTestUtils.setField(svc, "appId", appId);
        ReflectionTestUtils.setField(svc, "appSecret", appSecret);
        ReflectionTestUtils.setField(svc, "baseUrl", baseUrl != null ? baseUrl : "https://open.feishu.cn");
        return svc;
    }

    private static String yamlValue(String text, String key) {
        Pattern p = Pattern.compile("^\\s*" + Pattern.quote(key) + ":\\s*\"?([^\"\\n]+)\"?\\s*$",
                Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private FeishuProbeFactory() {
    }
}
