package com.smartmeeting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会议纪要持久化与 API 暴露配置，绑定 {@code meeting.minute.*} 前缀。
 */
@Data
@Component
@ConfigurationProperties(prefix = "meeting.minute")
public class MeetingMinuteProperties {

    /** 会后是否生成纪要（关闭后不触发 MeetingEndedEvent；离线转写由 meeting.asr.offline-enabled 独立控制） */
    private boolean generationEnabled = false;

    /** 是否在库内持久化纪要正文 */
    private boolean persistEnabled = true;

    /** GET /minute 是否返回全文（false 时仅返回 docUrl 等元数据） */
    private boolean exposeContentInApi = true;

    /**
     * 是否在 LLM 初稿后调用 AI 增强（OpenClaw {@code minute-enhancement} 或直调 LLM）。
     * false 时仅使用 Step 4 LLM 初稿，不经过 {@link com.smartmeeting.service.MinuteAIEnhancer}。
     */
    private boolean aiEnhancementEnabled = true;

    /**
     * 是否按会务类型将 SKILL.md 正文注入 LLM system prompt（直调 meeting.llm，不走 OpenClaw）。
     * false 时使用通用纪要 Prompt。
     */
    private boolean skillGenerationEnabled = true;

    /** 是否调用 LLM 生成结构化纪要初稿；false 时使用简易纪要（转写摘要 + 会议信息）。 */
    private boolean llmEnabled = true;

    /** 是否创建飞书文档并写入纪要正文。 */
    private boolean feishuDocEnabled = true;

    /** 纪要生成完成后是否推送飞书卡片/文本通知。 */
    private boolean notifyEnabled = true;
}
