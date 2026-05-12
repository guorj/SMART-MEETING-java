package com.smartmeeting.mybatis.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL {@code JSON} 列在部分驱动下以非 String 形式返回，导致实体 {@code String} 映射为 null。
 * 统一按 UTF-8 文本读回，写入仍用 {@link PreparedStatement#setString}。
 */
@MappedTypes(String.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.OTHER})
public class MysqlJsonAsStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object o = rs.getObject(columnName);
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        if (o instanceof Clob c) {
            return c.getSubString(1, (int) c.length());
        }
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object o = rs.getObject(columnIndex);
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        if (o instanceof Clob c) {
            return c.getSubString(1, (int) c.length());
        }
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object o = cs.getObject(columnIndex);
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        if (o instanceof Clob c) {
            return c.getSubString(1, (int) c.length());
        }
        return o.toString();
    }
}
