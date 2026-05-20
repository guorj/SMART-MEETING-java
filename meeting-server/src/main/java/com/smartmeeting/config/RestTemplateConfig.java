package com.smartmeeting.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * HTTP 客户端配置。
 * <p>
 * 统一 RestTemplate 字符串编解码为 UTF-8，避免 Windows 默认 GBK 影响 String 与部分 JSON 边界的编解码。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 注册 UTF-8 字符串消息转换器定制器。
     *
     * @return 将所有 {@link StringHttpMessageConverter} 默认字符集设为 UTF-8 的定制器
     */
    @Bean
    public RestTemplateCustomizer utf8StringHttpMessageConverterCustomizer() {
        return restTemplate -> restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(c -> c.setDefaultCharset(StandardCharsets.UTF_8));
    }

    /**
     * 提供默认 RestTemplate Bean（仅当容器中不存在其他 RestTemplate 时）。
     *
     * @param builder Spring Boot 自动配置的 RestTemplateBuilder
     * @return 配置完成的 RestTemplate 实例
     */
    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
