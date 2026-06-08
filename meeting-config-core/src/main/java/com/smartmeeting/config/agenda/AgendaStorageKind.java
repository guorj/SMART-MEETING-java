package com.smartmeeting.config.agenda;

/**
 * 会序资料存储类型。
 */
public final class AgendaStorageKind {

    public static final String FEISHU = "FEISHU";
    public static final String LOCAL = "LOCAL";

    private AgendaStorageKind() {
    }

    public static String normalize(String kind) {
        if (kind == null || kind.isBlank()) {
            return FEISHU;
        }
        return LOCAL.equalsIgnoreCase(kind.trim()) ? LOCAL : FEISHU;
    }
}
