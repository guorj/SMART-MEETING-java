package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

@Component
public class HostTtsEnabledDescriptor implements SystemConfigDescriptor {

    @Override
    public String key() {
        return "meeting.host.tts-enabled";
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
        return "主持 TTS 播报";
    }
}
