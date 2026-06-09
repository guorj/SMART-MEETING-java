package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

/** 只读展示 {@code meeting.audio.*} 协议/基础设施项（须改 YAML 并重启）。 */
abstract class AbstractInfraIntegerReadonlyDescriptor implements SystemConfigDescriptor {
    private final String key;
    private final String defaultValue;
    private final String description;
    private final int min;
    private final int max;

    AbstractInfraIntegerReadonlyDescriptor(String key, String defaultValue, String description, int min, int max) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.description = description;
        this.min = min;
        this.max = max;
    }

    @Override public String key() { return key; }
    @Override public String category() { return "infra"; }
    @Override public ConfigValueType type() { return ConfigValueType.INTEGER; }
    @Override public String defaultValue() { return defaultValue; }
    @Override public boolean hotReloadable() { return false; }
    @Override public boolean requiresRestart() { return true; }
    @Override public String description() { return description; }
    @Override public java.util.OptionalInt intMin() { return java.util.OptionalInt.of(min); }
    @Override public java.util.OptionalInt intMax() { return java.util.OptionalInt.of(max); }
}

@Component
class AudioSampleRateReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    AudioSampleRateReadonlyDescriptor() {
        super("meeting.audio.sample-rate", "16000", "PCM 采样率 Hz（讯飞协议默认）", 8000, 48000);
    }
}

@Component
class AudioMaxDurationHoursReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    AudioMaxDurationHoursReadonlyDescriptor() {
        super("meeting.audio.max-duration-hours", "4", "单场录音最长时长（小时）", 1, 24);
    }
}

@Component
class AudioCacheRetentionHoursReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    AudioCacheRetentionHoursReadonlyDescriptor() {
        super("meeting.audio.cache-retention-hours", "72", "本地 PCM 保留时长（小时）", 1, 720);
    }
}

@Component
class HostRuntimeTtsChunkBytesReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    HostRuntimeTtsChunkBytesReadonlyDescriptor() {
        super("meeting.host.runtime.tts-chunk-bytes", "6000", "主持 TTS WebSocket 单帧字节上限", 1000, 32000);
    }
}

@Component
class VoiceprintRegisterMinDurationReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    VoiceprintRegisterMinDurationReadonlyDescriptor() {
        super("meeting.voiceprint.register.min-duration-sec", "35", "声纹注册页最短录音秒数", 10, 120);
    }
}

@Component
class SessionFeishuPendingTtlReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    SessionFeishuPendingTtlReadonlyDescriptor() {
        super("meeting.session.feishu-start-pending-ttl-minutes", "30", "飞书建会多步会话 TTL（分钟）", 5, 120);
    }
}

@Component
class OpenClawPromptMaxCharsReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    OpenClawPromptMaxCharsReadonlyDescriptor() {
        super("openclaw.prompt-max-chars", "2000", "Agent prompt 转写截断字符数", 500, 8000);
    }
}

@Component
class MatterProgressFetchMaxSheetsReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    MatterProgressFetchMaxSheetsReadonlyDescriptor() {
        super("matter-progress.fetch.max-sheets", "20", "电子表格拉取最多工作表数", 1, 50);
    }
}

@Component
class FeishuDocBlockBatchSizeReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    FeishuDocBlockBatchSizeReadonlyDescriptor() {
        super("meeting.feishu.doc.block-batch-size", "50", "飞书 docx 块批量写入大小", 1, 100);
    }
}

@Component
class AsrRealtimePostOpenWaitMaxReadonlyDescriptor extends AbstractInfraIntegerReadonlyDescriptor {
    AsrRealtimePostOpenWaitMaxReadonlyDescriptor() {
        super("meeting.asr.realtime.post-open-wait-max-ms", "60000", "实时 ASR post-open-wait 上限 ms", 0, 120000);
    }
}
