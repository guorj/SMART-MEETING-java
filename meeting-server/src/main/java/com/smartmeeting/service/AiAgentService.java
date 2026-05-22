package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.service.agent.AgentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 调用门面。
 *
 * <p>按配置 {@code openclaw.agent.provider} 将三大 AI 增强场景委托给
 * {@link AgentProvider} 的具体实现（直调 LLM、OpenClaw Gateway MCP+Skill 等）。
 *
 * <p>介入场景：
 * <ul>
 *   <li>会议开始 — 上次待办进度 JSON 分析（{@link #analyzePreviousProgress}）</li>
 *   <li>主持会序 — OpenClaw 会序通报 Markdown（{@link #runAgendaBriefingViaOpenclaw}）</li>
 *   <li>纪要生成 — 初版纪要质量优化（{@link #enhanceMeetingMinutes}）</li>
 * </ul>
 */
@Slf4j
@Service
public class AiAgentService {

    private final AgentProvider provider;

    public AiAgentService(AgentProvider provider) {
        this.provider = provider;
        log.info("AiAgentService initialized with provider: {}", provider.getClass().getSimpleName());
    }

    /**
     * 分析上次会议待办进度，生成结构化 JSON 洞察。
     *
     * @return Agent 回复文本；不可用或失败时返回 {@code null}
     */
    public String analyzePreviousProgress(
            String meetingId,
            String previousMeetingId,
            String previousTitle,
            Map<String, Integer> todoStats,
            String delayedItems,
            String feishuMultitableDirective) {

        return provider.analyzePreviousProgress(
                meetingId, previousMeetingId, previousTitle,
                todoStats, delayedItems, feishuMultitableDirective);
    }

    /**
     * 主持会序通报（附带会序飞书 URL 与标题，供 matter-progress Skill 读取指定表/文档）。
     */
    public String runAgendaBriefingViaOpenclaw(Meeting meeting,
                                               String bitableDirective,
                                               String feishuUrl,
                                               String agendaTitle) {
        return provider.runMatterProgressReport(meeting, bitableDirective, feishuUrl, agendaTitle);
    }

    /**
     * 优化会议纪要。
     *
     * @return 优化后纪要；不可用或失败时返回 {@code rawMinute}
     */
    public String enhanceMeetingMinutes(
            String meetingId,
            String rawMinute,
            String meetingTitle,
            Integer meetingType,
            String participants,
            String transcriptText) {

        return provider.enhanceMeetingMinutes(
                meetingId, rawMinute, meetingTitle,
                meetingType, participants, transcriptText);
    }

    /**
     * 异步包装（不阻塞调用线程）。
     */
    @org.springframework.scheduling.annotation.Async
    public CompletableFuture<String> callAgentAsync(String prompt, String taskType) {
        log.warn("callAgentAsync is a legacy shim; prefer direct provider method calls. taskType={}", taskType);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 检查当前 Provider 是否可用。
     */
    public boolean isAgentAvailable() {
        return provider.isAvailable();
    }
}
