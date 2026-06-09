package com.smartmeeting.matterprogress.openclaw;

/**
 * OpenClaw Gateway {@code chat.history} 补全重试参数（对齐 openclaw.gateway.* YAML）。
 */
public final class OpenClawGatewayHistoryFetchSettings {

    public static final OpenClawGatewayHistoryFetchSettings DEFAULT =
            new OpenClawGatewayHistoryFetchSettings(24, 250, 16, 500);

    private final int attempts;
    private final int delayMs;
    private final int extendedAttempts;
    private final int extendedDelayMs;

    public OpenClawGatewayHistoryFetchSettings(int attempts, int delayMs,
                                               int extendedAttempts, int extendedDelayMs) {
        this.attempts = attempts;
        this.delayMs = delayMs;
        this.extendedAttempts = extendedAttempts;
        this.extendedDelayMs = extendedDelayMs;
    }

    public int attempts() {
        return attempts;
    }

    public int delayMs() {
        return delayMs;
    }

    public int extendedAttempts() {
        return extendedAttempts;
    }

    public int extendedDelayMs() {
        return extendedDelayMs;
    }
}
