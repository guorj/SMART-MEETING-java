package com.smartmeeting.config.feishu;

import com.smartmeeting.config.agenda.AgendaDocBindingSnapshot;
import com.smartmeeting.config.agenda.HostAgendaDocBinding;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FeishuResourceResolver {

    private static final Pattern DOCX_PATH = Pattern.compile("/docx/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_PATH = Pattern.compile("/wiki/([^/?#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_PATH = Pattern.compile("/base/([^/?#]+)", Pattern.CASE_INSENSITIVE);

    private FeishuResourceResolver() {
    }

    public static FeishuResourceRef resolve(HostAgendaDocBinding doc) {
        if (doc == null) {
            return null;
        }
        FeishuResourceRef ref = resolve(doc.getUrl());
        if (ref == null) {
            return null;
        }
        String mode = doc.getBitableDisplayMode();
        if (mode == null || mode.isBlank()) {
            return ref;
        }
        return new FeishuResourceRef(
                ref.kind(), ref.primaryToken(), ref.tableId(), ref.viewId(), ref.sourceUrl(), mode.trim());
    }

    public static FeishuResourceRef resolve(AgendaDocBindingSnapshot cfg) {
        if (cfg == null) {
            return null;
        }
        FeishuResourceRef ref = resolve(cfg.getFeishuDocUrl());
        if (ref == null) {
            return null;
        }
        String mode = cfg.getBitableDisplayMode();
        if (mode == null || mode.isBlank()) {
            return ref;
        }
        return new FeishuResourceRef(
                ref.kind(), ref.primaryToken(), ref.tableId(), ref.viewId(), ref.sourceUrl(), mode.trim());
    }

    public static FeishuResourceRef resolve(String feishuDocUrl) {
        String url = feishuDocUrl != null ? feishuDocUrl.trim() : "";
        if (url.isEmpty()) {
            return null;
        }
        return resolveFromUrl(url);
    }

    public static boolean isRecognizedFeishuDocUrl(String url) {
        String u = url != null ? url.trim() : "";
        if (u.isEmpty()) {
            return false;
        }
        return resolveFromUrl(u) != null;
    }

    public static String resolveLegacyDocumentId(AgendaDocBindingSnapshot cfg) {
        FeishuResourceRef ref = resolve(cfg);
        if (ref == null || ref.primaryToken() == null || ref.primaryToken().isBlank()) {
            return null;
        }
        return ref.primaryToken();
    }

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

    private static FeishuResourceRef resolveFromUrl(String url) {
        Matcher docx = DOCX_PATH.matcher(url);
        if (docx.find()) {
            return new FeishuResourceRef(FeishuResourceKind.DOCX, docx.group(1), null, null, url, null);
        }
        Matcher wiki = WIKI_PATH.matcher(url);
        if (wiki.find()) {
            String tableId = queryParam(url, "table");
            String viewId = queryParam(url, "view");
            return new FeishuResourceRef(FeishuResourceKind.WIKI, wiki.group(1), tableId, viewId, url, null);
        }
        Matcher base = BASE_PATH.matcher(url);
        if (base.find()) {
            String appToken = base.group(1);
            String tableId = queryParam(url, "table");
            String viewId = queryParam(url, "view");
            return new FeishuResourceRef(FeishuResourceKind.BASE, appToken, tableId, viewId, url, null);
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
