package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

@Component
public class HostEnabledDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.host.enabled";
    }

    @Override
    public String category() {
        return "host";
    }

    @Override
    public ConfigValueType type() {
        return ConfigValueType.BOOLEAN;
    }

    @Override
    public String defaultValue() {
        return "true";
    }

    @Override
    public boolean hotReloadable() {
        return true;
    }

    @Override
    public String description() {
        return "是否启用 AI 主持";
    }
}
