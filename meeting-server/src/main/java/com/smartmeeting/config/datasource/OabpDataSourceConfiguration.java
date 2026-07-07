package com.smartmeeting.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * oabp 外部库数据源与会话工厂，供 {@code com.smartmeeting.repository.oabp} 包下的 Mapper 使用。
 * <p>
 * 启用条件：{@code meeting.datasource.external.oabp.enabled=true}。
 * </p>
 * <p>
 * 与主库 {@link PrimaryDataSourceConfiguration} 并存且明确隔离：
 * <ul>
 *   <li>独立 Hikari 连接池 {@code oabpDataSource}；</li>
 *   <li>独立 {@link SqlSessionFactory} {@code oabpSqlSessionFactory}，mapper-locations 限定
 *       {@code classpath*:/mapper/oabp/&#42;&#42;/&#42;.xml}，避免与主库 Mapper XML 冲突；</li>
 *   <li>独立事务管理器 {@code oabpTransactionManager}，业务方法需显式
 *       {@code @Transactional("oabpTransactionManager")} 才能写入 oabp；</li>
 *   <li>Mapper 扫描仅 {@code com.smartmeeting.repository.oabp} 子包，主库 Mapper 不受影响。</li>
 * </ul>
 * </p>
 * <p>
 * <b>职责边界</b>：本配置注册的 SqlSessionFactory 与 Mapper 供后台业务代码读写 oabp 使用；
 * 前台会序资料 {@code oabpTaskSql} 仍由 {@link com.smartmeeting.service.oabp.OabpAgendaTaskQueryService}
 * 经 {@link com.smartmeeting.service.oabp.OabpReadOnlySqlValidator} 校验后以 JdbcTemplate 只读执行，
 * 两条路径互不影响。
 * </p>
 * <p>
 * <b>跨库事务</b>：oabp 与主库 {@code intelligence} 虽常驻同一 MySQL 实例，但本配置使用独立连接池，
 * 无法与主库共享 JDBC 连接，因此 {@code @Transactional} 不能跨库。需要跨库一致性的场景应走
 * {@code int_event_outbox} + 异步消费者（见 {@code com.smartmeeting.service.oabp.outbox}）。
 * </p>
 *
 * @see PrimaryDataSourceConfiguration
 * @see OabpDataSourceProperties
 */
@Configuration
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OabpDataSourceProperties.class)
@MapperScan(
        basePackages = "com.smartmeeting.repository.oabp",
        sqlSessionFactoryRef = "oabpSqlSessionFactory"
)
public class OabpDataSourceConfiguration {

    /** oabp Mapper XML 加载路径，与主库 mapper-locations 隔离。 */
    private static final String OABP_MAPPER_LOCATIONS = "classpath*:/mapper/oabp/**/*.xml";

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

    /**
     * oabp 库 MyBatis-Plus 会话工厂，仅扫描 {@link #OABP_MAPPER_LOCATIONS}。
     * <p>
     * 复用全局 {@link MybatisPlusProperties} 的 configuration（map-underscore-to-camel-case 等）
     * 与 globalConfig（id-type、逻辑删除等），保证与主库一致的开发体验；但 mapper-locations 显式覆盖，
     * 避免加载主库 XML。
     * </p>
     *
     * @param oabpDataSource      oabp 连接池
     * @param mybatisPlusProperties 全局 MyBatis-Plus 配置（来自 {@code application.yml}）
     * @return oabp {@link SqlSessionFactory}，Bean 名 {@code oabpSqlSessionFactory}
     * @throws Exception 创建会话工厂失败时
     */
    @Bean("oabpSqlSessionFactory")
    public SqlSessionFactory oabpSqlSessionFactory(
            @Qualifier("oabpDataSource") DataSource oabpDataSource,
            MybatisPlusProperties mybatisPlusProperties) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(oabpDataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        if (mybatisPlusProperties.getConfiguration() != null) {
            mybatisPlusProperties.getConfiguration().applyTo(configuration);
        }
        factory.setConfiguration(configuration);

        if (mybatisPlusProperties.getGlobalConfig() != null) {
            factory.setGlobalConfig(mybatisPlusProperties.getGlobalConfig());
        }

        // 显式指定 oabp 专属 XML 路径，不继承全局 mapper-locations，避免误加载主库 XML
        factory.setMapperLocations(
                new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                        .getResources(OABP_MAPPER_LOCATIONS));

        return factory.getObject();
    }

    /**
     * oabp 库事务管理器，业务方法写入 oabp 时需显式
     * {@code @Transactional("oabpTransactionManager")}。
     *
     * @param oabpDataSource oabp 连接池
     * @return oabp {@link PlatformTransactionManager}，Bean 名 {@code oabpTransactionManager}
     */
    @Bean("oabpTransactionManager")
    public PlatformTransactionManager oabpTransactionManager(
            @Qualifier("oabpDataSource") DataSource oabpDataSource) {
        return new DataSourceTransactionManager(oabpDataSource);
    }
}
