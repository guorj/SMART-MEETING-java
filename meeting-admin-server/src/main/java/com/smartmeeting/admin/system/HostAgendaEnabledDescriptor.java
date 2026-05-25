package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

@Component
public class HostAgendaEnabledDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.host.agenda-enabled";
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
        return "会序推进（下一议题/跳过）";
    }
}
