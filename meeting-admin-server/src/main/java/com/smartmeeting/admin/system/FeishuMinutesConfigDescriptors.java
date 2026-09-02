package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 妙记 File B 媒体下载鉴权（{@code user_access_token}）。
 * <p>
 * 部分租户 {@code tenant_access_token} 调 {@code /media} 会 2091005，API 调试台用用户 token 可成功。
 */
@Component
class FeishuMinutesUserRefreshTokenDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.feishu.minutes.user-refresh-token";
    }

    @Override
    public String category() {
        return "vc";
    }

    @Override
    public ConfigValueType type() {
        return ConfigValueType.STRING;
    }

    @Override
    public String defaultValue() {
        return "";
    }

    @Override
    public boolean hotReloadable() {
        return true;
    }

    @Override
    public String description() {
        return "妙记 /media 下载 OAuth refresh_token（推荐；meeting-server 自动刷新 user_access_token）";
    }

    @Override
    public boolean sensitive() {
        return true;
    }

    @Override
    public Optional<String> parentKey() {
        return Optional.of("meeting.vc.recording-enabled");
    }
}

@Component
class FeishuMinutesUserAccessTokenDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.feishu.minutes.user-access-token";
    }

    @Override
    public String category() {
        return "vc";
    }

    @Override
    public ConfigValueType type() {
        return ConfigValueType.STRING;
    }

    @Override
    public String defaultValue() {
        return "";
    }

    @Override
    public boolean hotReloadable() {
        return true;
    }

    @Override
    public String description() {
        return "妙记 /media 下载 user_access_token（约 2h，调试用；与 refresh_token 同时配置时以 refresh 为准）";
    }

    @Override
    public boolean sensitive() {
        return true;
    }

    @Override
    public Optional<String> parentKey() {
        return Optional.of("meeting.vc.recording-enabled");
    }
}
