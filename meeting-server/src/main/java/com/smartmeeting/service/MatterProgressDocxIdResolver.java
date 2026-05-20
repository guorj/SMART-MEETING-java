package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;

/**
 * 事项进度通报：从配置解析飞书资源主 token（docx / wiki / base）。
 *
 * <p>对 {@link MatterProgressDocConfig#getFeishuDocUrl()} 做薄封装，
 * 实际解析委托 {@link com.smartmeeting.service.feishu.FeishuResourceResolver}。
 */
public final class MatterProgressDocxIdResolver {

    private MatterProgressDocxIdResolver() {
    }

    /**
     * 从配置的 {@code feishu_doc_url} 解析主 token（docx document_id / wiki node / base app）。
     *
     * @param cfg 事项进度文档配置，可为 null
     * @return 解析出的主 token；无法解析或未配置时返回 null
     */
    public static String resolveDocumentId(MatterProgressDocConfig cfg) {
        return com.smartmeeting.service.feishu.FeishuResourceResolver.resolveLegacyDocumentId(cfg);
    }

    /**
     * 判断配置是否已填写飞书文档相关字段（用于区分「未配置」与「已填但解析失败」）。
     *
     * @param cfg 事项进度文档配置
     * @return {@code feishu_doc_url} 非空时为 true
     */
    public static boolean hasFeishuFields(MatterProgressDocConfig cfg) {
        return cfg.getFeishuDocUrl() != null && !cfg.getFeishuDocUrl().isBlank();
    }
}
