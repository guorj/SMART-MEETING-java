package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

@Component
class AsrRealtimeEnabledDescriptor extends AbstractBooleanDescriptor {
    AsrRealtimeEnabledDescriptor() {
        super("meeting.asr.realtime-enabled", "asr", "false", "会中是否启用实时 ASR 字幕");
    }
}

@Component
class AsrOfflineEnabledDescriptor extends AbstractBooleanDescriptor {
    AsrOfflineEnabledDescriptor() {
        super("meeting.asr.offline-enabled", "asr", "true", "会后是否启用离线转写");
    }
}

@Component
class AsrOfflineRoleEnabledDescriptor extends AbstractBooleanDescriptor {
    AsrOfflineRoleEnabledDescriptor() {
        super("meeting.asr.offline-role-enabled", "asr", "true", "离线 IST 是否开启说话人分离",
                "meeting.asr.offline-enabled");
    }
}

@Component
class AsrOfflineRoleModeDescriptor extends AbstractStringDescriptor {
    AsrOfflineRoleModeDescriptor() {
        super("meeting.asr.offline-role-mode", "asr", "auto",
                "离线角色分离模式（auto|blind|voiceprint）", "meeting.asr.offline-enabled");
    }
}

@Component
class AsrOfflineRoleNumHintEnabledDescriptor extends AbstractBooleanDescriptor {
    AsrOfflineRoleNumHintEnabledDescriptor() {
        super("meeting.asr.offline-role-num-hint-enabled", "asr", "true",
                "是否向 IST 传 roleNum 参会人数 hint", "meeting.asr.offline-enabled");
    }
}
