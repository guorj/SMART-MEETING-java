package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openclaw")
public class OpenClawProperties {

    private boolean enabled = true;
    private boolean skillMode = true;
    private int maxConcurrentInvokes = 5;
    private int timeoutSeconds = 60;
    /** Agent 任务 prompt 中转写正文最大字符数。 */
    private int promptMaxChars = 2000;
    private Agent agent = new Agent();
    private Gateway gateway = new Gateway();

    @Data
    public static class Agent {
        private String provider = "mcp";
    }

    @Data
    public static class Gateway {
        private int historyFetchAttempts = 24;
        private int historyFetchDelayMs = 250;
        private int historyFetchExtendedAttempts = 16;
        private int historyFetchExtendedDelayMs = 500;
    }
}
