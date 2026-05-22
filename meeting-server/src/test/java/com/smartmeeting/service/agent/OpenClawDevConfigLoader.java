package com.smartmeeting.service.agent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 {@code application-dev.yml} 读取 {@code openclaw.*} 默认值（与 dev profile 启动一致）。
 */
final class OpenClawDevConfigLoader {

    record DevOpenClawConfig(
            boolean enabled,
            String provider,
            String gatewayUrl,
            String sessionKey,
            int timeoutSeconds,
            boolean skillMode) {
    }

    static DevOpenClawConfig load() {
        try {
            String text = readDevYmlText();
            int start = text.indexOf("\nopenclaw:");
            if (start < 0) {
                start = text.indexOf("openclaw:");
            }
            if (start < 0) {
                throw new IllegalStateException("application-dev.yml 缺少 openclaw 配置节");
            }
            String block = text.substring(start);
            return new DevOpenClawConfig(
                    parseBool(block, "enabled", true),
                    yamlValue(block, "provider", "mcp"),
                    yamlValue(block, "gateway-url", "http://127.0.0.1:18789"),
                    yamlValue(block, "agent-session-key",
                            "agent:jqclaw:direct:ou_5eadac907bd2dc79e8b39205ef6bf33a"),
                    parseInt(block, "timeout-seconds", 120),
                    parseBool(block, "skill-mode", true));
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 application-dev.yml openclaw 配置: " + e.getMessage(), e);
        }
    }

    private static String yamlValue(String block, String key, String fallback) {
        Pattern p = Pattern.compile("^\\s*" + Pattern.quote(key) + ":\\s*(.+?)\\s*$", Pattern.MULTILINE);
        Matcher m = p.matcher(block);
        if (!m.find()) {
            return fallback;
        }
        return resolvePlaceholderDefault(m.group(1).trim());
    }

    private static boolean parseBool(String block, String key, boolean fallback) {
        String v = yamlValue(block, key, String.valueOf(fallback));
        return Boolean.parseBoolean(v);
    }

    private static int parseInt(String block, String key, int fallback) {
        String v = yamlValue(block, key, String.valueOf(fallback));
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 解析 {@code ${ENV:default}}，取第一个 {@code :} 与 {@code }} 之间的默认值（支持 URL 内含冒号）。 */
    static String resolvePlaceholderDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            int colon = trimmed.indexOf(':');
            if (colon > 2) {
                return trimmed.substring(colon + 1, trimmed.length() - 1).trim();
            }
            return "";
        }
        return trimmed.replace("\"", "");
    }

    private static String readDevYmlText() throws Exception {
        try (InputStream in = OpenClawDevConfigLoader.class.getResourceAsStream("/application-dev.yml")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        Path[] candidates = {
                Paths.get("src/main/resources/application-dev.yml"),
                Paths.get("meeting-server/src/main/resources/application-dev.yml"),
                Paths.get("meeting-server/target/classes/application-dev.yml")
        };
        for (Path yml : candidates) {
            if (Files.isRegularFile(yml)) {
                return Files.readString(yml, StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException(
                "找不到 application-dev.yml（请先 mvn compile，或把 Working directory 设为 meeting-server）");
    }

    private OpenClawDevConfigLoader() {
    }
}
