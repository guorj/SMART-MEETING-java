package com.smartmeeting.config.datasource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * oabp 外部库数据源（仅 JDBC，供 {@link com.smartmeeting.service.oabp.OabpAgendaTaskQueryService}）。
 * <p>
 * 启用条件：{@code meeting.datasource.external.oabp.enabled=true}。
 * 不在此注册 MyBatis {@code SqlSessionFactory}，避免与主库 Mapper 冲突。
 */
@Configuration
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OabpDataSourceProperties.class)
public class OabpDataSourceConfiguration {

    /**
     * oabp 库 Hikari 连接池。
     *
     * @param oabpProperties 绑定 {@code meeting.datasource.external.oabp} 的连接参数
     * @return oabp {@link DataSource}，Bean 名 {@code oabpDataSource}
     */
    @Bean("oabpDataSource")
    public DataSource oabpDataSource(OabpDataSourceProperties oabpProperties) {
        return oabpProperties.toHikariDataSource();
    }
}
