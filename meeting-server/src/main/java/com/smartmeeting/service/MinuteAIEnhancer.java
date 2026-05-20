package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 会议纪要的 AI 增强处理器（纪要生成环节）。
 *
 * <p>在 LLM 产出初版纪要后，调用 {@link AiAgentService#enhanceMeetingMinutes} 做质量优化与结构化校验；
 * 解析失败或 Agent 不可用时原样返回初版纪要。可选方法用于从 AI 完整 JSON 中提取质量报告、
 * 待办高亮与发言人要点。
 *
 * <p>主要协作：{@link AiAgentService}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinuteAIEnhancer {

    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 优化会议纪要
     *
     * @param meetingId 会议ID
     * @param rawMinute LLM生成的初版纪要
     * @param meetingTitle 会议主题
     * @param meetingType 会议类型（1-6）
     * @param participants 参会人列表（逗号分隔）
     * @param transcriptText 转写原文（用于校验，可为空）
     * @return 优化后的纪要正文；AI 失败、解析失败或未返回有效字段时返回 {@code rawMinute}
     */
    public String enhanceMinute(
            String meetingId,
            String rawMinute,
            String meetingTitle,
            Integer meetingType,
            String participants,
            String transcriptText) {

        log.info("开始纪要优化: meetingId={}, type={}", meetingId, meetingType);

        // 🤖 调用 AI Agent 优化
        String aiResult = null;
        try {
            aiResult = aiAgentService.enhanceMeetingMinutes(
                    meetingId,
                    rawMinute,
                    meetingTitle,
                    meetingType,
                    participants,
                    transcriptText
            );

            if (aiResult != null && !aiResult.isEmpty()) {
                log.info("AI Agent minute enhancement success: meetingId={}", meetingId);
            }

        } catch (Exception e) {
            log.warn("AI Agent enhancement failed: {}", e.getMessage());
        }

        // 解析AI返回结果，提取优化后的纪要
        if (aiResult != null) {
            try {
                JsonNode result = objectMapper.readTree(aiResult);

                // 提取优化后的纪要正文
                String optimizedMinute = result.path("optimized_minute").asText("");

                if (!optimizedMinute.isEmpty()) {
                    // 记录质量检查结果
                    JsonNode qualityCheck = result.path("quality_check");
                    if (!qualityCheck.isMissingNode()) {
                        int score = qualityCheck.path("score").asInt(0);
                        log.info("纪要质量评分: {}分", score);

                        // 如果有缺失信息，记录日志
                        JsonNode issues = qualityCheck.path("issues");
                        if (issues.isArray() && issues.size() > 0) {
                            StringBuilder issuesText = new StringBuilder("纪要质量问题: ");
                            for (JsonNode issue : issues) {
                                issuesText.append(issue.asText()).append("; ");
                            }
                            log.warn(issuesText.toString());
                        }
                    }

                    // 返回优化后的纪要
                    log.info("纪要优化完成: meetingId={}, 原长度={}, 优化后长度={}",
                            meetingId, rawMinute.length(), optimizedMinute.length());
                    return optimizedMinute;
                }

            } catch (Exception e) {
                log.error("AI结果解析失败，返回原纪要: {}", e.getMessage());
            }
        }

        // AI失败或解析失败，返回原纪要
        log.info("纪要优化失败或未返回有效结果，使用原纪要: meetingId={}", meetingId);
        return rawMinute;
    }

    /**
     * 获取纪要质量检查报告（可选）
     *
     * @param aiResult AI 返回的完整 JSON 字符串
     * @return 含 {@code score}、{@code issues} 的报告 Map；无质量块或解析失败时返回 {@code null}
     */
    public Map<String, Object> extractQualityCheckReport(String aiResult) {
        if (aiResult == null || aiResult.isEmpty()) {
            return null;
        }

        try {
            JsonNode result = objectMapper.readTree(aiResult);
            JsonNode qualityCheck = result.path("quality_check");

            if (qualityCheck.isMissingNode()) {
                return null;
            }

            Map<String, Object> report = new java.util.HashMap<>();
            report.put("score", qualityCheck.path("score").asInt(0));

            java.util.List<String> issues = new java.util.ArrayList<>();
            JsonNode issuesNode = qualityCheck.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode issue : issuesNode) {
                    issues.add(issue.asText());
                }
            }
            report.put("issues", issues);

            return report;

        } catch (Exception e) {
            log.warn("质量检查报告提取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取重点待办标注（可选）
     *
     * @param aiResult AI 返回的完整 JSON 字符串
     * @return 重点待办列表，每项含 {@code content}、{@code priority}、{@code assignee}；无数据时返回空列表
     */
    public java.util.List<Map<String, String>> extractTodoHighlights(String aiResult) {
        if (aiResult == null || aiResult.isEmpty()) {
            return java.util.List.of();
        }

        try {
            JsonNode result = objectMapper.readTree(aiResult);
            JsonNode todoHighlight = result.path("todo_highlight");

            if (!todoHighlight.isArray()) {
                return java.util.List.of();
            }

            java.util.List<Map<String, String>> highlights = new java.util.ArrayList<>();
            for (JsonNode todo : todoHighlight) {
                Map<String, String> item = new java.util.HashMap<>();
                item.put("content", todo.path("content").asText());
                item.put("priority", todo.path("priority").asText("MEDIUM"));
                item.put("assignee", todo.path("assignee").asText(""));
                highlights.add(item);
            }

            return highlights;

        } catch (Exception e) {
            log.warn("待办标注提取失败: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * 提取各发言人发言要点归类（可选）
     *
     * @param aiResult AI 返回的完整 JSON 字符串
     * @return 发言人要点列表，每项含 {@code speaker}、{@code key_points}；无数据时返回空列表
     */
    public java.util.List<Map<String, Object>> extractSpeakerSummary(String aiResult) {
        if (aiResult == null || aiResult.isEmpty()) {
            return java.util.List.of();
        }

        try {
            JsonNode result = objectMapper.readTree(aiResult);
            JsonNode speakerSummary = result.path("speaker_summary");

            if (!speakerSummary.isArray()) {
                return java.util.List.of();
            }

            java.util.List<Map<String, Object>> summaries = new java.util.ArrayList<>();
            for (JsonNode speaker : speakerSummary) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("speaker", speaker.path("speaker").asText());

                java.util.List<String> points = new java.util.ArrayList<>();
                JsonNode keyPoints = speaker.path("key_points");
                if (keyPoints.isArray()) {
                    for (JsonNode point : keyPoints) {
                        points.add(point.asText());
                    }
                }
                item.put("key_points", points);

                summaries.add(item);
            }

            return summaries;

        } catch (Exception e) {
            log.warn("发言人要点提取失败: {}", e.getMessage());
            return java.util.List.of();
        }
    }
}