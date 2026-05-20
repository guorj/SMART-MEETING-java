package com.smartmeeting.service.agent;

import com.smartmeeting.entity.Meeting;

import java.util.Map;

/**
 * AI Agent 能力提供者接口。
 *
 * <p>定义三大 AI 增强场景的统一抽象，由具体实现决定底层调用方式
 * （OpenClaw CLI、直调 LLM、MCP 等）。
 *
 * <p>返回 {@code null} 表示不可用或调用失败，由调用方决定降级策略。
 */
public interface AgentProvider {

    /**
     * 分析上次会议待办进度，生成结构化 JSON 洞察。
     *
     * @param meetingId                 当前会议 ID
     * @param previousMeetingId         上次会议 ID
     * @param previousTitle             上次会议标题
     * @param todoStats                 待办统计（completed / inProgress / delayed）
     * @param delayedItems              延期项详情文本
     * @param feishuMultitableDirective 飞书多维表读取指令（可空）
     * @return Agent 回复文本（通常为 JSON）；不可用或失败时返回 {@code null}
     */
    String analyzePreviousProgress(String meetingId,
                                   String previousMeetingId,
                                   String previousTitle,
                                   Map<String, Integer> todoStats,
                                   String delayedItems,
                                   String feishuMultitableDirective);

    /**
     * 生成事项进度通报 Markdown。
     *
     * @param meeting          当前会议
     * @param bitableDirective 多维表读取指令（可空）；为空时由实现决定是否降级
     * @return Markdown 正文；不可用或失败时返回 {@code null}
     */
    String runMatterProgressReport(Meeting meeting, String bitableDirective);

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
