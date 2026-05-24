package com.smartmeeting.service.agent;

import com.smartmeeting.entity.Meeting;

import java.util.Map;

/**
 * AI Agent 能力提供者接口。
 *
 * <p>定义 AI 增强场景的统一抽象，由具体实现决定底层调用方式
 * （直调 LLM、OpenClaw Gateway MCP+Skill 等）。
 *
 * <p>返回 {@code null} 表示不可用或调用失败，由调用方决定降级策略。
 *
 * <p>会前进度（上次待办进度卡片 + progress-analysis Skill）已在 v0.9 下线，
 * 由 feishu-scheduled-bot + matter-progress-core 的「会前事项对比通报」取代。
 */
public interface AgentProvider {

    /**
     * 生成事项进度通报 Markdown。
     *
     * @param meeting          当前会议
     * @param bitableDirective 多维表读取指令（可空）；为空时由实现决定是否降级
     * @return Markdown 正文；不可用或失败时返回 {@code null}
     */
    String runMatterProgressReport(Meeting meeting, String bitableDirective);

    /**
     * 主持会序通报：在 {@link #runMatterProgressReport} 基础上附带会序飞书 URL 与标题（Skill 可选参数）。
     * 默认实现忽略额外参数，仅使用 {@code bitableDirective}。
     */
    default String runMatterProgressReport(Meeting meeting,
                                           String bitableDirective,
                                           String feishuUrl,
                                           String agendaTitle) {
        return runMatterProgressReport(meeting, bitableDirective);
    }

    /**
     * 主持会序通报（附带会序索引与 requestId，供 Gateway 会话隔离与 cache-bust）。
     */
    default String runMatterProgressReport(Meeting meeting,
                                           String bitableDirective,
                                           String feishuUrl,
                                           String agendaTitle,
                                           int agendaIndex,
                                           String requestId) {
        return runMatterProgressReport(meeting, bitableDirective, feishuUrl, agendaTitle, agendaIndex, requestId, null);
    }

    /**
     * 主持会序通报（附带 taskId，用于 Gateway idempotencyKey 与响应隔离校验）。
     */
    default String runMatterProgressReport(Meeting meeting,
                                           String bitableDirective,
                                           String feishuUrl,
                                           String agendaTitle,
                                           int agendaIndex,
                                           String requestId,
                                           String taskId) {
        return runMatterProgressReport(meeting, bitableDirective, feishuUrl, agendaTitle, agendaIndex, requestId);
    }

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
