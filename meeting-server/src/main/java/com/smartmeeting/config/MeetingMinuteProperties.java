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

    /** 会后是否生成纪要（关闭后不触发 MeetingEndedEvent 纪要链路） */
    private boolean generationEnabled = true;

    /** 是否在库内持久化纪要正文 */
    private boolean persistEnabled = true;

    /** GET /minute 是否返回全文（false 时仅返回 docUrl 等元数据） */
    private boolean exposeContentInApi = true;

    /**
     * 是否在 LLM 初稿后调用 AI 增强（OpenClaw {@code minute-enhancement} 或直调 LLM）。
     * false 时仅使用 Step 4 LLM 初稿，不经过 {@link com.smartmeeting.service.MinuteAIEnhancer}。
     */
    private boolean aiEnhancementEnabled = true;
}
