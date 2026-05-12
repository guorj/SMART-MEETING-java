package com.smartmeeting.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${meeting.web.static-cache-seconds:300}")
    private int staticCacheSeconds;

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
