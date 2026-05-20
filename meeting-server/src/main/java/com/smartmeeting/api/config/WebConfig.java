package com.smartmeeting.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC Web 层配置。
 * <p>
 * 配置 REST API 与 WebSocket 的 CORS，以及录音页、静态资源、AudioWorklet 脚本等资源映射。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${meeting.web.static-cache-seconds:300}")
    private int staticCacheSeconds;

    /**
     * 注册跨域规则：{@code /api/**} 与 {@code /ws/**} 允许任意来源与方法。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);

        // WebSocket 跨域
        registry.addMapping("/ws/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST")
                .allowedHeaders("*");
    }

    /**
     * 注册静态资源处理器：录音子目录、通用 static、worklet 脚本。
     *
     * @param registry 资源处理器注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 录音页面: /recorder/{meetingId}
        registry.addResourceHandler("/recorder/**")
                .addResourceLocations("classpath:/static/recorder/")
                .resourceChain(true);

        // 静态资源: /static/**（勿对单文件再注册一条 handler，Spring 6 下会解析失败 → 404）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(staticCacheSeconds);

        // worklet: /worklet/** (AudioWorklet 脚本)
        registry.addResourceHandler("/worklet/**")
                .addResourceLocations("classpath:/static/worklet/");
    }
}
