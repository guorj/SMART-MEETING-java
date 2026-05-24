package com.smartmeeting.matterprogress.openclaw;

/**
 * OpenClaw Gateway 会话键解析。
 */
public final class OpenClawSessionKeys {

    private OpenClawSessionKeys() {
    }

    /** 每个 taskId 独立 sessionKey，避免 Gateway 把不同任务结果复用到同一会话。 */
    public static String resolveForTask(String taskId, String baseSessionKey) {
        String base = baseSessionKey != null && !baseSessionKey.isBlank()
                ? baseSessionKey.trim()
                : "agent:openclaw";
        String tid = taskId != null ? taskId.trim() : "unknown";
        if (tid.length() > 120) {
            tid = tid.substring(0, 120);
        }
        return base + ":task:" + tid;
    }
}
