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
 * 统一 HTTP 客户端为 UTF-8，避免 Windows 默认 GBK 影响 String 与部分 JSON 边界的编解码。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplateCustomizer utf8StringHttpMessageConverterCustomizer() {
        return restTemplate -> restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(c -> c.setDefaultCharset(StandardCharsets.UTF_8));
    }

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
