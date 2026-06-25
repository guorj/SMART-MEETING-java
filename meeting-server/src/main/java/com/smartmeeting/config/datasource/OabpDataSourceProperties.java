package com.smartmeeting.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * oabp 外部库连接配置，绑定 {@code meeting.datasource.external.oabp}。
 * <p>
 * 与主库 {@code intelligence} 通常共用 {@code DB_HOST}/{@code DB_PORT}/{@code DB_USERNAME}，
 * JDBC URL 中库名由 {@code DB_OABP_NAME}（默认 {@code oabp}）区分。
 */
@Data
@ConfigurationProperties(prefix = "meeting.datasource.external.oabp")
public class OabpDataSourceProperties {

    /** 是否注册 oabp 连接池与 {@code com.smartmeeting.repository.oabp} Mapper 扫描。 */
    private boolean enabled;

    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private Hikari hikari = new Hikari();
    /** 单条 oabpTaskSql 最大返回行数 */
    private int maxQueryRows = 500;
    /** JDBC 查询超时（秒） */
    private int queryTimeoutSeconds = 10;

    /**
     * 根据当前属性构建 Hikari 连接池。
     *
     * @return 已配置 JDBC URL/账号与池参数的 {@link HikariDataSource}
     */
    public HikariDataSource toHikariDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setPoolName("oabp-pool");
        ds.setMaximumPoolSize(hikari.getMaximumPoolSize());
        ds.setMinimumIdle(hikari.getMinimumIdle());
        ds.setConnectionTimeout(hikari.getConnectionTimeout());
        return ds;
    }

    @Data
    public static class Hikari {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 10_000L;
    }
}
