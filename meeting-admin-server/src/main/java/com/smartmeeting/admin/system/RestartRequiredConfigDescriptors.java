package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

@Component
class KafkaEnabledReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "meeting.kafka.enabled"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.BOOLEAN; }
    @Override public String defaultValue() { return "false"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "Kafka 异步（false=LocalEventBus）"; }
}

@Component
class TodoReminderEnabledReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "meeting.todo.reminder-enabled"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.BOOLEAN; }
    @Override public String defaultValue() { return "false"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "待办到期提醒 Cron 任务"; }
}

@Component
class CacheRefreshEnabledReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "meeting.cache.refresh-enabled"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.BOOLEAN; }
    @Override public String defaultValue() { return "true"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "会务预设 Redis 缓存定时刷新"; }
}

@Component
class RecordingTimeoutCheckReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "meeting.recording.timeout-check-enabled"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.BOOLEAN; }
    @Override public String defaultValue() { return "true"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "录音超时自动结束（4h）"; }
}

@Component
class OpenClawProviderReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "openclaw.agent.provider"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.STRING; }
    @Override public String defaultValue() { return "mcp"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "Agent 实现（mcp|llm）"; }
}

@Component
class SchedulerScanMsReadonlyDescriptor implements SystemConfigDescriptor {
    @Override public String key() { return "meeting.scheduler.scan-ms"; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.INTEGER; }
    @Override public String defaultValue() { return "300000"; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return "会前调度扫描周期（毫秒）"; }
}
