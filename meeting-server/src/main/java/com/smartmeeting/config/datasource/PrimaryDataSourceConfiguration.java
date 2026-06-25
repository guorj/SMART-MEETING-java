package com.smartmeeting.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 主库 {@code intelligence} 数据源与 MyBatis 会话工厂。
 * <p>
 * 显式 {@link Primary}，与可选 oabp 外部库并存时保证 {@code com.smartmeeting.repository} Mapper 绑定主库。
 */
@Configuration
public class PrimaryDataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource primaryDataSource(DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            DataSource primaryDataSource,
            MybatisPlusProperties mybatisPlusProperties) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(primaryDataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        if (mybatisPlusProperties.getConfiguration() != null) {
            mybatisPlusProperties.getConfiguration().applyTo(configuration);
        }
        factory.setConfiguration(configuration);
        if (mybatisPlusProperties.getGlobalConfig() != null) {
            factory.setGlobalConfig(mybatisPlusProperties.getGlobalConfig());
        }
        if (mybatisPlusProperties.getMapperLocations() != null
                && mybatisPlusProperties.getMapperLocations().length > 0) {
            factory.setMapperLocations(mybatisPlusProperties.resolveMapperLocations());
        }
        return factory.getObject();
    }

    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }
}
