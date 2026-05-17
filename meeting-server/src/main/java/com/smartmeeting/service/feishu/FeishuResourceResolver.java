package com.smartmeeting.service.feishu;

import com.smartmeeting.entity.MatterProgressDocConfig;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从配置 URL 解析飞书资源类型：docx / wiki / base（多维表格）。
 */
public final class FeishuResourceResolver {

    private static final Pattern DOCX_PATH = Pattern.compile("/docx/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_PATH = Pattern.compile("/wiki/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_PATH = Pattern.compile("/base/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private FeishuResourceResolver() {
    }

    public static FeishuResourceRef resolve(MatterProgressDocConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return resolve(cfg.getFeishuDocUrl());
    }

    public static FeishuResourceRef resolve(String feishuDocUrl) {
        String url = feishuDocUrl != null ? feishuDocUrl.trim() : "";
        if (url.isEmpty()) {
            return null;
        }
        return resolveFromUrl(url);
    }

    /** 是否为可识别的飞书 docx / wiki / base 链接（拒绝 /rec/、/host/ 等应用内地址）。 */
    public static boolean isRecognizedFeishuDocUrl(String url) {
        String u = url != null ? url.trim() : "";
        if (u.isEmpty()) {
            return false;
        }
        return resolveFromUrl(u) != null;
    }

    /**
     * 兼容旧 host_agenda：仅保存 document_id 时拼成 docx 链接再解析。
     */
    public static String legacyDocIdToDocxUrl(String legacyDocId) {
        if (legacyDocId == null || legacyDocId.isBlank()) {
            return null;
        }
        String t = legacyDocId.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        return "https://bytedance.feishu.cn/docx/" + t;
    }

    /**
     * 兼容旧逻辑：返回主 token（docx id / wiki node / base app）。
     */
    public static String resolveLegacyDocumentId(MatterProgressDocConfig cfg) {
        FeishuResourceRef ref = resolve(cfg);
        if (ref == null || ref.primaryToken() == null || ref.primaryToken().isBlank()) {
            return null;
        }
        return ref.primaryToken();
    }

    private static FeishuResourceRef resolveFromUrl(String url) {
        Matcher docx = DOCX_PATH.matcher(url);
        if (docx.find()) {
            return new FeishuResourceRef(FeishuResourceKind.DOCX, docx.group(1), null, null, url);
        }
        Matcher wiki = WIKI_PATH.matcher(url);
        if (wiki.find()) {
            String tableId = queryParam(url, "table");
            String viewId = queryParam(url, "view");
            return new FeishuResourceRef(FeishuResourceKind.WIKI, wiki.group(1), tableId, viewId, url);
        }
        Matcher base = BASE_PATH.matcher(url);
        if (base.find()) {
            String appToken = base.group(1);
            String tableId = queryParam(url, "table");
            String viewId = queryParam(url, "view");
            return new FeishuResourceRef(FeishuResourceKind.BASE, appToken, tableId, viewId, url);
        }
        return null;
    }

    private static String queryParam(String url, String name) {
        try {
            String q = URI.create(url).getRawQuery();
            if (q == null || q.isBlank()) {
                return null;
            }
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                if (name.equalsIgnoreCase(part.substring(0, eq).trim())) {
                    return part.substring(eq + 1).trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
