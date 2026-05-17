package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;

/**
 * 事项进度通报：从配置解析飞书资源主 token（docx / wiki / base）。
 */
public final class MatterProgressDocxIdResolver {

    private MatterProgressDocxIdResolver() {
    }

    /**
     * 从 {@code feishu_doc_url} 解析主 token（docx id / wiki node / base app）。
     */
    public static String resolveDocumentId(MatterProgressDocConfig cfg) {
        return com.smartmeeting.service.feishu.FeishuResourceResolver.resolveLegacyDocumentId(cfg);
    }

    public static boolean hasFeishuFields(MatterProgressDocConfig cfg) {
        return cfg.getFeishuDocUrl() != null && !cfg.getFeishuDocUrl().isBlank();
    }
}
