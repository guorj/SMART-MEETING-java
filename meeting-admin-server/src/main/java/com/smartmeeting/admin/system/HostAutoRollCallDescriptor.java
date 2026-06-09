package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HostAutoRollCallDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.host.auto-roll-call-after-opening";
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
        return "开场白后自动进入检点（须议程含检点且启用检点）";
    }

    @Override
    public Optional<String> parentKey() {
        return Optional.of("meeting.host.roll-call-enabled");
    }
}
