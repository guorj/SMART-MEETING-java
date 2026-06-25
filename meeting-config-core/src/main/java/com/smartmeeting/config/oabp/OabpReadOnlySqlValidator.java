package com.smartmeeting.config.oabp;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * oabp 会序资料 SQL 只读校验：仅允许单条 {@code SELECT}，禁止 DML/DDL 与多语句。
 */
public final class OabpReadOnlySqlValidator {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|GRANT|REVOKE|CALL|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE);

    private OabpReadOnlySqlValidator() {
    }

    /**
     * @param sql host_agenda 配置的 oabpTaskSql
     * @return 去掉首尾空白后的 SQL
     * @throws IllegalArgumentException 非 SELECT、含分号或含禁止关键字时
     */
    public static String validateAndNormalize(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("oabpTaskSql 不能为空");
        }
        String normalized = sql.strip().replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.indexOf(';') >= 0) {
            throw new IllegalArgumentException("oabpTaskSql 不允许包含分号（禁止多语句）");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT")) {
            throw new IllegalArgumentException("oabpTaskSql 必须以 SELECT 开头");
        }
        if (FORBIDDEN.matcher(normalized).find()) {
            throw new IllegalArgumentException("oabpTaskSql 包含禁止的关键字");
        }
        return normalized;
    }

    /**
     * 校验可选 SQL；空白视为未配置。
     *
     * @return 问题描述；无问题时返回 {@code null}
     */
    public static String validateOptional(String sql) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        try {
            validateAndNormalize(sql);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
