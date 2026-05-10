package com.smartmeeting.service;

import com.smartmeeting.entity.MatterProgressDocConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事项进度通报：从配置解析飞书 Docx 的 {@code document_id}。
 */
public final class MatterProgressDocxIdResolver {

    private static final Pattern DOCX_PATH = Pattern.compile("/docx/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private MatterProgressDocxIdResolver() {
    }

    /**
     * 优先使用 {@code feishu_doc_token}（即文档 document_id）；否则从 {@code feishu_doc_url} 中匹配 {@code .../docx/{id}}。
     *
     * @return document_id，无法解析时返回 null
     */
    public static String resolveDocumentId(MatterProgressDocConfig cfg) {
        if (cfg.getFeishuDocToken() != null) {
            String t = cfg.getFeishuDocToken().trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        String url = cfg.getFeishuDocUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = DOCX_PATH.matcher(url.trim());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 是否显式配置了飞书侧字段（URL 或 token 非空），用于区分「走飞书」与「仅 classpath」。
     */
    public static boolean hasFeishuFields(MatterProgressDocConfig cfg) {
        return (cfg.getFeishuDocToken() != null && !cfg.getFeishuDocToken().isBlank())
                || (cfg.getFeishuDocUrl() != null && !cfg.getFeishuDocUrl().isBlank());
    }
}
