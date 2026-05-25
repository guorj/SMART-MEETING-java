package com.smartmeeting.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "meeting.admin.oauth.feishu")
public class FeishuOAuthProperties {
    private boolean enabled = false;
    private String appId = "";
    private String appSecret = "";
    /** 回调地址，须与飞书应用配置一致，如 http://127.0.0.1:8766/admin/oauth/feishu/callback */
    private String redirectUri = "http://127.0.0.1:8766/admin/oauth/feishu/callback";
    /** 允许登录的飞书 user_id 列表，空=不限制（仅建议 dev） */
    private List<String> allowedUserIds = new ArrayList<>();
    private long sessionTtlHours = 8;
}
