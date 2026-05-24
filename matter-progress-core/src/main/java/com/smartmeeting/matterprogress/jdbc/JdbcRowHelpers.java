package com.smartmeeting.matterprogress.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/** JDBC 行读取工具（MySQL 数值列可能返回 Long，避免 (Integer) 强转失败）。 */
public final class JdbcRowHelpers {

    private JdbcRowHelpers() {
    }

    public static Integer readInteger(ResultSet rs, String column) throws SQLException {
        Object v = rs.getObject(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return Integer.parseInt(s);
    }

    public static boolean readEnabledFlag(ResultSet rs, String column) throws SQLException {
        Object v = rs.getObject(column);
        if (v == null) {
            return false;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        String s = v.toString().trim();
        return "1".equals(s) || "true".equalsIgnoreCase(s);
    }
}
