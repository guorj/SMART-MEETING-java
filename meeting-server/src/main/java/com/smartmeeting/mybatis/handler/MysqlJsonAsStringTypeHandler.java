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
 * MySQL {@code JSON} 列类型处理器。
 * <p>
 * 部分 JDBC 驱动下 JSON 列以非 String 形式返回，导致实体 {@code String} 映射为 null。
 * 本处理器统一按 UTF-8 文本读回，写入仍用 {@link PreparedStatement#setString}。
 */
@MappedTypes(String.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.LONGVARCHAR, JdbcType.OTHER})
public class MysqlJsonAsStringTypeHandler extends BaseTypeHandler<String> {

    /**
     * 将非空 JSON 字符串写入 PreparedStatement。
     *
     * @param ps        PreparedStatement
     * @param i         参数索引（1-based）
     * @param parameter JSON 字符串
     * @param jdbcType  JDBC 类型（本实现忽略，统一 setString）
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    /**
     * 从 ResultSet 按列名读取 JSON 列并转为 String。
     *
     * @param rs         ResultSet
     * @param columnName 列名
     * @return UTF-8 文本，null 表示数据库值为 NULL
     * @throws SQLException 读取失败时抛出
     */
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

    /**
     * 从 ResultSet 按列索引读取 JSON 列并转为 String。
     *
     * @param rs          ResultSet
     * @param columnIndex 列索引（1-based）
     * @return UTF-8 文本，null 表示数据库值为 NULL
     * @throws SQLException 读取失败时抛出
     */
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

    /**
     * 从 CallableStatement 按列索引读取 JSON 列并转为 String。
     *
     * @param cs          CallableStatement
     * @param columnIndex 列索引（1-based）
     * @return UTF-8 文本，null 表示数据库值为 NULL
     * @throws SQLException 读取失败时抛出
     */
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
