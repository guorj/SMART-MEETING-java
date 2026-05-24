package com.smartmeeting.service;

import com.smartmeeting.service.agent.AgentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 调用门面。
 *
 * <p>按配置 {@code openclaw.agent.provider} 将 AI 增强场景委托给
 * {@link AgentProvider} 的具体实现（直调 LLM、OpenClaw Gateway MCP+Skill 等）。
 *
 * <p>介入场景：
 * <ul>
 *   <li>纪要生成 — 初版纪要质量优化（{@link #enhanceMeetingMinutes}）</li>
 * </ul>
 *
 * <p>会前进度（上次待办进度卡片）已在 v0.9 下线，由 feishu-scheduled-bot 的「会前事项对比通报」取代。
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
