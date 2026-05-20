package com.smartmeeting.api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 客户端配置。
 * <p>
 * 仅在 classpath 存在 {@link RedisConnectionFactory} 时生效，提供字符串键值型的 {@link RedisTemplate}。
 */
@Configuration
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisConfig {

    /**
     * 创建键与值均为字符串序列化的 Redis 模板 Bean。
     *
     * @param connectionFactory Spring Data Redis 连接工厂
     * @return 配置完成的 {@link RedisTemplate}
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
