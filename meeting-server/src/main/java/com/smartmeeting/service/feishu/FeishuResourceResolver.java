package com.smartmeeting.service.feishu;

import com.smartmeeting.entity.MatterProgressDocConfig;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书资源 URL 解析器：从配置 URL 或旧版 document_id 识别 docx / wiki / base（多维表格）类型。
 * <p>
 * 主要协作组件：{@link FeishuResourceRef}（解析结果载体）、{@link MatterProgressDocConfig}（数据库配置来源）。
 */
public final class FeishuResourceResolver {

    private static final Pattern DOCX_PATH = Pattern.compile("/docx/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_PATH = Pattern.compile("/wiki/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_PATH = Pattern.compile("/base/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private FeishuResourceResolver() {
    }

    /**
     * 从资料配置实体解析飞书资源引用。
     *
     * @param cfg 资料配置（含 feishuDocUrl）
     * @return 解析后的资源引用；cfg 为 null 或 URL 无效时返回 {@code null}
     */
    public static FeishuResourceRef resolve(MatterProgressDocConfig cfg) {
        if (cfg == null) {
            return null;
        }
        return resolve(cfg.getFeishuDocUrl());
    }

    /**
     * 从飞书文档 URL 字符串解析资源引用。
     *
     * @param feishuDocUrl 飞书 HTTPS 链接
     * @return 解析后的资源引用；URL 为空或无法识别时返回 {@code null}
     */
    public static FeishuResourceRef resolve(String feishuDocUrl) {
        String url = feishuDocUrl != null ? feishuDocUrl.trim() : "";
        if (url.isEmpty()) {
            return null;
        }
        return resolveFromUrl(url);
    }

    /**
     * 判断是否为可识别的飞书 docx / wiki / base 链接（拒绝 /rec/、/host/ 等应用内地址）。
     *
     * @param url 待检测 URL
     * @return 可识别返回 {@code true}；为空或无法识别时返回 {@code false}
     */
    public static boolean isRecognizedFeishuDocUrl(String url) {
        String u = url != null ? url.trim() : "";
        if (u.isEmpty()) {
            return false;
        }
        return resolveFromUrl(u) != null;
    }

    /**
     * 兼容旧 host_agenda：仅保存 document_id 时拼成 docx 链接。
     *
     * @param legacyDocId 旧版 document_id 或完整 URL
     * @return docx HTTPS 链接；legacyDocId 为空时返回 {@code null}
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
     * 兼容旧逻辑：从配置返回主 token（docx id / wiki node / base app）。
     *
     * @param cfg 资料配置
     * @return 主 token；无法解析时返回 {@code null}
     */
    public static String resolveLegacyDocumentId(MatterProgressDocConfig cfg) {
        FeishuResourceRef ref = resolve(cfg);
        if (ref == null || ref.primaryToken() == null || ref.primaryToken().isBlank()) {
            return null;
        }
        return ref.primaryToken();
    }

    /** 从 URL 路径与 query 参数解析资源类型与 token。 */
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

    /** 从 URL query 字符串提取指定参数值。 */
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
                    String raw = part.substring(eq + 1).trim();
                    try {
                        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        return raw;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
