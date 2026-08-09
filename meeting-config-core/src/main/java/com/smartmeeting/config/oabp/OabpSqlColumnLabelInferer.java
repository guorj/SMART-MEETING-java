package com.smartmeeting.config.oabp;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从 SELECT 列表推断列 source → 中文/AS 别名 label。 */
public final class OabpSqlColumnLabelInferer {

    private static final Pattern AS_PATTERN = Pattern.compile(
            "(?i)([`\"']?)([\\w\\u4e00-\\u9fff]+)\\1\\s+AS\\s+([`'\"]?)([\\w\\u4e00-\\u9fff]+)\\3");
    private static final Pattern TRAILING_ALIAS = Pattern.compile(
            "(?i)^(.+?)\\s+([`'\"]?)([\\w\\u4e00-\\u9fff]+)\\2$");

    private OabpSqlColumnLabelInferer() {
    }

    /**
     * @param sql       oabp SQL
     * @param headers   探测到的列名（source）
     * @return source → 建议 label；无 AS 时不包含该键
     */
    public static Map<String, String> infer(String sql, Iterable<String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (sql == null || sql.isBlank() || headers == null) {
            return out;
        }
        String selectList = extractSelectList(sql);
        if (selectList.isBlank()) {
            return out;
        }
        Map<String, String> aliasByExpr = parseSelectItems(selectList);
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                continue;
            }
            String h = header.trim();
            if (aliasByExpr.containsKey(h)) {
                out.put(h, aliasByExpr.get(h));
                continue;
            }
            for (Map.Entry<String, String> e : aliasByExpr.entrySet()) {
                if (h.equalsIgnoreCase(e.getValue()) || h.equalsIgnoreCase(e.getKey())) {
                    out.put(h, e.getValue());
                    break;
                }
            }
        }
        return out;
    }

    private static String extractSelectList(String sql) {
        String normalized = sql.replaceAll("\\s+", " ").trim();
        int sel = normalized.toLowerCase(Locale.ROOT).indexOf("select ");
        int from = normalized.toLowerCase(Locale.ROOT).indexOf(" from ");
        if (sel < 0 || from <= sel) {
            return "";
        }
        return normalized.substring(sel + 7, from).trim();
    }

    private static Map<String, String> parseSelectItems(String selectList) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : splitSelectList(selectList)) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            Matcher asMatcher = AS_PATTERN.matcher(item);
            if (asMatcher.find()) {
                String source = asMatcher.group(2);
                String alias = asMatcher.group(4);
                map.put(alias, alias);
                if (!source.equalsIgnoreCase(alias)) {
                    map.put(source, alias);
                }
                continue;
            }
            Matcher trail = TRAILING_ALIAS.matcher(item);
            if (trail.matches() && !trail.group(1).contains("(")) {
                String expr = trail.group(1).trim();
                String alias = trail.group(3);
                String source = expr.contains(".") ? expr.substring(expr.lastIndexOf('.') + 1) : expr;
                source = source.replaceAll("[`\"']", "");
                map.put(alias, alias);
                if (!source.equalsIgnoreCase(alias)) {
                    map.put(source, alias);
                }
                continue;
            }
            String bare = item.replaceAll("[`\"']", "");
            if (bare.matches("[\\w\\u4e00-\\u9fff]+")) {
                map.put(bare, bare);
            }
        }
        return map;
    }

    private static java.util.List<String> splitSelectList(String selectList) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < selectList.length(); i++) {
            char c = selectList.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        return parts;
    }
}
