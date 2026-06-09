package com.smartmeeting.admin.system;

import com.smartmeeting.config.system.ConfigValueType;
import com.smartmeeting.config.system.SystemConfigDescriptor;
import org.springframework.stereotype.Component;

@Component
class PipelinePreOnCreateDescriptor extends AbstractBooleanDescriptor {
    PipelinePreOnCreateDescriptor() {
        super("meeting.pipeline.pre-on-create-enabled", "pipeline", "false", "建会成功后是否立即跑 PRE 流水线");
    }
}

@Component
class PipelinePostAutoTriggerEnabledDescriptor extends AbstractBooleanDescriptor {
    PipelinePostAutoTriggerEnabledDescriptor() {
        super("meeting.pipeline.post-auto-trigger.enabled", "pipeline", "false",
                "会议结束后是否自动触发 POST 流水线");
    }
}

@Component
class PipelinePostTemplateCodeDescriptor extends AbstractStringDescriptor {
    PipelinePostTemplateCodeDescriptor() {
        super("meeting.pipeline.post-auto-trigger.template-code", "pipeline", "",
                "POST 模板编码（空=默认启用模板）", "meeting.pipeline.post-auto-trigger.enabled");
    }
}

@Component
class SchedulerPreEnabledDescriptor extends AbstractBooleanDescriptor {
    SchedulerPreEnabledDescriptor() {
        super("meeting.scheduler.pre-enabled", "scheduler", "false", "会前 PRE 自动调度总开关");
    }
}

@Component
class SchedulerPreWindowMinutesDescriptor extends AbstractIntegerDescriptor {
    SchedulerPreWindowMinutesDescriptor() {
        super("meeting.scheduler.pre-window-minutes", "scheduler", "5",
                "会前触发容忍窗口（分钟）", "meeting.scheduler.pre-enabled");
    }
}

@Component
class SchedulerPre24hTemplateDescriptor extends AbstractStringDescriptor {
    SchedulerPre24hTemplateDescriptor() {
        super("meeting.scheduler.pre-24h-template-code", "scheduler", "",
                "会前 24h PRE 模板编码", "meeting.scheduler.pre-enabled");
    }
}

@Component
class SchedulerPre10mTemplateDescriptor extends AbstractStringDescriptor {
    SchedulerPre10mTemplateDescriptor() {
        super("meeting.scheduler.pre-10m-template-code", "scheduler", "",
                "会前 10min PRE 模板编码", "meeting.scheduler.pre-enabled");
    }
}

@Component
class NotificationBotEnabledDescriptor extends AbstractBooleanDescriptor {
    NotificationBotEnabledDescriptor() {
        super("meeting.notification.bot-enabled", "notification", "false",
                "会后通知是否经 feishu-scheduled-bot 中转");
    }
}

@Component
class NotificationFallbackDirectDescriptor extends AbstractBooleanDescriptor {
    NotificationFallbackDirectDescriptor() {
        super("meeting.notification.fallback-direct", "notification", "true",
                "Bot 不可用时是否回退直连飞书 API", "meeting.notification.bot-enabled");
    }
}

@Component
class VoiceprintOfflineLabelDescriptor extends AbstractBooleanDescriptor {
    VoiceprintOfflineLabelDescriptor() {
        super("meeting.voiceprint.offline-label-enabled", "asr", "true",
                "离线转写后是否 ISV 声纹标注", "meeting.isv.enabled");
    }
}

@Component
class IsvEnabledDescriptor extends AbstractBooleanDescriptor {
    IsvEnabledDescriptor() {
        super("meeting.isv.enabled", "asr", "false",
                "是否启用 ISV 声纹 1:N", "meeting.asr.offline-enabled");
    }
}

@Component
class WebStaticCacheSecondsDescriptor extends AbstractIntegerDescriptor {
    WebStaticCacheSecondsDescriptor() {
        super("meeting.web.static-cache-seconds", "web", "300", "静态资源浏览器缓存秒数");
    }
}

@Component
class WebPageCacheBusterDescriptor extends AbstractStringDescriptor {
    WebPageCacheBusterDescriptor() {
        super("meeting.web.page-cache-buster", "web", "", "录音/主持页 URL 缓存破除参数 &v=");
    }
}

@Component
class OpenClawEnabledDescriptor extends AbstractBooleanDescriptor {
    OpenClawEnabledDescriptor() {
        super("openclaw.enabled", "openclaw", "true", "是否启用 OpenClaw 纪要增强");
    }
}

@Component
class OpenClawSkillModeDescriptor extends AbstractBooleanDescriptor {
    OpenClawSkillModeDescriptor() {
        super("openclaw.skill-mode", "openclaw", "true", "OpenClaw Skill 模式",
                "openclaw.enabled");
    }
}

@Component
class OpenClawTimeoutSecondsDescriptor extends AbstractIntegerDescriptor {
    OpenClawTimeoutSecondsDescriptor() {
        super("openclaw.timeout-seconds", "openclaw", "60", "OpenClaw invoke 超时（秒）",
                "openclaw.enabled");
    }
}

@Component
class OpenClawMaxConcurrentDescriptor extends AbstractIntegerDescriptor {
    OpenClawMaxConcurrentDescriptor() {
        super("openclaw.max-concurrent-invokes", "openclaw", "5", "OpenClaw 并行 invoke 上限",
                "openclaw.enabled");
    }
}
