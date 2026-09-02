package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.OpenClawProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 直调 LLM Chat Completions 的 AgentProvider 实现。
 *
 * <p>复用 {@code meeting.llm.*} 配置，将 AgentProvider 场景
 * 转化为 Chat Completions 请求，不依赖 OpenClaw Gateway。
 *
 * <p>由配置 {@code openclaw.agent.provider=llm} 激活；
 * 当 {@code openclaw.agent.provider} 未配置时也默认激活（{@code matchIfMissing}）。
 *
 * <p>会前 matter-progress 已在 v0.9 下线；本 Provider 仅用于 Step 4.1 纪要增强。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "openclaw.agent.provider", havingValue = "llm", matchIfMissing = true)
public class DirectLlmAgentProvider implements AgentProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenClawProperties openClawProperties;

    @Value("${meeting.llm.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String llmApiUrl;

    @Value("${meeting.llm.api-key:}")
    private String llmApiKey;

    @Value("${meeting.llm.model:deepseek-chat}")
    private String llmModel;

    public DirectLlmAgentProvider(RestTemplate restTemplate, OpenClawProperties openClawProperties) {
        this.restTemplate = restTemplate;
        this.openClawProperties = openClawProperties;
    }

    @Override
    public String enhanceMeetingMinutes(String meetingId,
                                        String rawMinute,
                                        String meetingTitle,
                                        Integer meetingType,
                                        String participants,
                                        String transcriptText) {
        String systemPrompt = "你是会议纪要优化助手。请根据用户提供的初版纪要输出优化后的 JSON，"
                + "严格按指定格式输出。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("会议信息：\n");
        userPrompt.append("- 主题：").append(meetingTitle).append("\n");
        userPrompt.append("- 类型：").append(getMeetingTypeName(meetingType)).append("\n");
        userPrompt.append("- 参会人：").append(participants != null ? participants : "未知").append("\n\n");
        userPrompt.append("初版纪要：\n---\n").append(rawMinute).append("\n---\n\n");
        if (transcriptText != null && transcriptText.length() > 100) {
            userPrompt.append("转写原文片段（校验用）：\n");
            userPrompt.append(transcriptText.substring(0,
                    Math.min(openClawProperties.getPromptMaxChars(), transcriptText.length())));
            userPrompt.append("\n\n");
        }
        userPrompt.append("请输出以下 JSON（不要代码块包裹）：\n");
        userPrompt.append("{\n");
        userPrompt.append("  \"optimized_minute\": \"优化后的完整纪要\",\n");
        userPrompt.append("  \"quality_check\": { \"score\": 85, \"issues\": [\"...\"] },\n");
        userPrompt.append("  \"missing_info\": [\"...\"],\n");
        userPrompt.append("  \"speaker_summary\": [{\"speaker\": \"...\", \"key_points\": [\"...\"]}],\n");
        userPrompt.append("  \"todo_highlight\": [{\"content\": \"...\", \"priority\": \"HIGH\", \"assignee\": \"...\"}]\n");
        userPrompt.append("}");

        String result = callLlm(systemPrompt, userPrompt.toString(), "minute_enhancement");
        return result != null ? result : rawMinute;
    }

    @Override
    public boolean isAvailable() {
        return llmApiKey != null && !llmApiKey.isBlank();
    }

    /**
     * 调用 LLM Chat Completions。
     *
     * @return 回复文本；失败时返回 {@code null}
     */
    private String callLlm(String systemPrompt, String userPrompt, String taskType) {
        log.info("DirectLlm Agent call: taskType={}, promptLength={}", taskType, userPrompt.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (llmApiKey != null && !llmApiKey.isBlank()) {
            headers.setBearerAuth(llmApiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("temperature", 0.35);
        body.put("max_tokens", 4000);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = resolveChatCompletionsUrl();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode json = response.getBody();
            if (json != null && json.has("choices")) {
                String text = json.get("choices").get(0).path("message").path("content").asText();
                log.info("DirectLlm Agent success: taskType={}, replyLength={}", taskType, text.length());
                return text;
            }
        } catch (Exception e) {
            log.warn("DirectLlm Agent call failed: taskType={}, error={}", taskType, e.getMessage());
        }
        return null;
    }

    private String resolveChatCompletionsUrl() {
        String u = llmApiUrl != null ? llmApiUrl.trim() : "";
        if (u.contains("chat/completions")) {
            return u;
        }
        String base = u.isEmpty() ? "https://api.deepseek.com" : u.replaceAll("/$", "");
        return base + "/v1/chat/completions";
    }

    private String getMeetingTypeName(Integer typeCode) {
        if (typeCode == null) {
            return "临时会议";
        }
        return switch (typeCode) {
            case 1 -> "综合管理会";
            case 2 -> "技术委员会";
            case 3 -> "市场经营会";
            case 4 -> "财务月会";
            case 5 -> "经营委员会";
            case 6 -> "临时会议";
            default -> "未知类型";
        };
    }
}
