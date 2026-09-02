package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 妙记 Open API 鉴权补充配置（{@code meeting.feishu.minutes.*}）。
 * <p>
 * 飞书 {@code GET /minutes/v1/minutes/{token}/media} 在部分租户下
 * {@code tenant_access_token} 会返回 {@code 2091005}，而 API 调试台用
 * {@code user_access_token} 可成功（见开放平台 minute-media/get 文档）。
 * 本配置用于会后自动拉取 File B 时优先使用用户身份。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.feishu.minutes")
public class MeetingFeishuMinutesProperties {

    /**
     * 妙记媒体下载用的 {@code user_access_token}（约 2h 有效，适合调试）。
     * 与 {@link #userRefreshToken} 同时配置时以 refresh 为准。
     */
    private String userAccessToken = "";

    /**
     * 妙记媒体下载用的用户 refresh_token（OAuth 授权后获得，长期有效）。
     * meeting-server 会按需刷新并缓存 {@code user_access_token}。
     */
    private String userRefreshToken = "";
}
