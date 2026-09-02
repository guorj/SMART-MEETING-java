package com.smartmeeting.service.agent;

/**
 * AI Agent 能力提供者接口。
 *
 * <p>定义纪要 AI 增强场景的统一抽象，由具体实现决定底层调用方式
 * （直调 LLM、OpenClaw Gateway MCP+Skill 等）。
 *
 * <p>返回 {@code null} 表示不可用或调用失败，由调用方决定降级策略。
 */
public interface AgentProvider {

    /**
     * 优化会议纪要。
     *
     * @param meetingId      会议 ID
     * @param rawMinute      LLM 生成的初版纪要
     * @param meetingTitle   会议主题
     * @param meetingType    会议类型（1-6）
     * @param participants   参会人列表（逗号分隔）
     * @param transcriptText 转写原文片段（可空）
     * @return 优化后的纪要正文；不可用或失败时返回 {@code rawMinute}
     */
    String enhanceMeetingMinutes(String meetingId,
                                 String rawMinute,
                                 String meetingTitle,
                                 Integer meetingType,
                                 String participants,
                                 String transcriptText);

    /**
     * 检查当前 Provider 是否可用。
     *
     * @return 可用时为 {@code true}
     */
    boolean isAvailable();
}
