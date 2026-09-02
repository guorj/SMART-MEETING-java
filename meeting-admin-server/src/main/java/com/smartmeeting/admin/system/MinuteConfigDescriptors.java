package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

@Component
class MinuteGenerationEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteGenerationEnabledDescriptor() {
        super("meeting.minute.generation-enabled", "minute", "false", "会后是否自动生成纪要");
    }
}

@Component
class MinuteLlmEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteLlmEnabledDescriptor() {
        super("meeting.minute.llm-enabled", "minute", "true", "未绑定 Skill 时是否调用 LLM 生成纪要初稿",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinuteSkillGenerationEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteSkillGenerationEnabledDescriptor() {
        super("meeting.minute.skill-generation-enabled", "minute", "true",
                "是否按会务类型将 SKILL.md 注入 LLM system prompt",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinuteAiEnhancementEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteAiEnhancementEnabledDescriptor() {
        super("meeting.minute.ai-enhancement-enabled", "minute", "true", "LLM 初稿后是否 AI 增强",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinuteFeishuDocEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteFeishuDocEnabledDescriptor() {
        super("meeting.minute.feishu-doc-enabled", "minute", "true", "是否创建/更新飞书纪要文档",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinuteNotifyEnabledDescriptor extends AbstractBooleanDescriptor {
    MinuteNotifyEnabledDescriptor() {
        super("meeting.minute.notify-enabled", "minute", "true", "纪要完成后是否推送飞书通知",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinutePersistEnabledDescriptor extends AbstractBooleanDescriptor {
    MinutePersistEnabledDescriptor() {
        super("meeting.minute.persist-enabled", "minute", "true", "是否将纪要正文写入库表",
                "meeting.minute.generation-enabled");
    }
}

@Component
class MinuteExposeContentInApiDescriptor extends AbstractBooleanDescriptor {
    MinuteExposeContentInApiDescriptor() {
        super("meeting.minute.expose-content-in-api", "minute", "true", "GET 纪要 API 是否返回全文",
                "meeting.minute.generation-enabled");
    }
}
