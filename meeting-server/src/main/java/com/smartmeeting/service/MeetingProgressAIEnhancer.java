package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 上次会议待办进度 - AI增强处理器
 *
 * 【环节1介入】在会议开始时，调用AI Agent分析上次会议待办进度，
 * 生成智能洞察和建议，替代原有简单的数字统计卡片
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingProgressAIEnhancer {

    private final AiAgentService aiAgentService;
    private final MeetingMapper meetingMapper;
    private final OpenclawComprehensiveBitableBranch comprehensiveBitableBranch;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 分析上次会议待办进度，返回智能分析结果
     *
     * @param currentMeetingId 当前会议ID
     * @param previousMeetingId 上次会议ID
     * @param previousTitle 上次会议标题
     * @param todos 上次会议待办列表
     * @return 智能分析结果（JSON格式），用于构建飞书卡片
     */
    public String analyzeAndEnhance(
            String currentMeetingId,
            String previousMeetingId,
            String previousTitle,
            List<MeetingTodo> todos) {

        // 统计数据
        int completed = (int) todos.stream()
                .filter(t -> TodoStatus.COMPLETED.name().equals(t.getStatus()))
                .count();
        int inProgress = (int) todos.stream()
                .filter(t -> TodoStatus.IN_PROGRESS.name().equals(t.getStatus()))
                .count();
        int delayed = (int) todos.stream()
                .filter(t -> TodoStatus.DELAYED.name().equals(t.getStatus()))
                .count();

        // 构建延期项详情文本
        String delayedItemsText = buildDelayedItemsText(todos, delayed);

        // 统计数据Map
        Map<String, Integer> todoStats = new HashMap<>();
        todoStats.put("completed", completed);
        todoStats.put("inProgress", inProgress);
        todoStats.put("delayed", delayed);

        String feishuMultitableDirective = buildFeishuMultitableDirectiveIfApplicable(currentMeetingId);

        // 🤖 调用 AI Agent 分析
        try {
            String aiResult = aiAgentService.analyzePreviousProgress(
                    currentMeetingId,
                    previousMeetingId,
                    previousTitle,
                    todoStats,
                    delayedItemsText,
                    feishuMultitableDirective
            );

            if (aiResult != null && !aiResult.isEmpty()) {
                log.info("AI Agent progress analysis success: meetingId={}", currentMeetingId);
                return aiResult;
            }

        } catch (Exception e) {
            log.warn("AI Agent analysis failed: {}", e.getMessage());
        }

        // AI失败时返回null，由调用方决定是否降级
        return null;
    }

    private String buildFeishuMultitableDirectiveIfApplicable(String currentMeetingId) {
        if (currentMeetingId == null) {
            return null;
        }
        Meeting current = meetingMapper.selectById(currentMeetingId);
        return comprehensiveBitableBranch.buildDirectiveForPreviousMeetingProgress(current);
    }

    /**
     * 根据AI分析结果构建飞书卡片元素
     *
     * @param aiAnalysisResult AI返回的JSON分析结果
     * @param previousTitle 上次会议标题
     * @return 飞书卡片元素列表
     */
    public List<Map<String, String>> buildSmartCardElements(
            String aiAnalysisResult,
            String previousTitle) {

        List<Map<String, String>> elements = new java.util.ArrayList<>();

        try {
            JsonNode analysis = objectMapper.readTree(aiAnalysisResult);

            // 1. 进度概述
            String progressSummary = analysis.path("progress_summary").asText("");
            if (!progressSummary.isEmpty()) {
                Map<String, String> summary = new HashMap<>();
                summary.put("content", "## 📊 上次会议待办进度\n\n**" + previousTitle + "**\n\n" + progressSummary);
                elements.add(summary);
            }

            // 2. 延期原因分析
            JsonNode delayReasons = analysis.path("delay_reasons");
            if (delayReasons.isArray() && delayReasons.size() > 0) {
                StringBuilder reasonsText = new StringBuilder("**⚠️ 延期原因分析:**\n\n");
                for (JsonNode reason : delayReasons) {
                    reasonsText.append("- **").append(reason.path("category").asText())
                            .append("**: ").append(reason.path("detail").asText())
                            .append(" (").append(reason.path("count").asInt()).append("项)\n");
                }
                Map<String, String> reasonsSection = new HashMap<>();
                reasonsSection.put("content", reasonsText.toString());
                elements.add(reasonsSection);
            }

            // 3. 高优先级提醒
            JsonNode alerts = analysis.path("high_priority_alerts");
            if (alerts.isArray() && alerts.size() > 0) {
                StringBuilder alertsText = new StringBuilder("**🔥 高优先级待办提醒:**\n\n");
                for (JsonNode alert : alerts) {
                    alertsText.append("🔴 ").append(alert.path("content").asText())
                            .append(" (责任人: ").append(alert.path("assignee").asText()).append(")\n");
                }
                Map<String, String> alertsSection = new HashMap<>();
                alertsSection.put("content", alertsText.toString());
                elements.add(alertsSection);
            }

            // 4. 本次会议建议
            JsonNode recommendations = analysis.path("recommendations");
            if (recommendations.isArray() && recommendations.size() > 0) {
                StringBuilder recText = new StringBuilder("**💡 本次会议推进建议:**\n\n");
                for (JsonNode rec : recommendations) {
                    recText.append("- ").append(rec.asText()).append("\n");
                }
                Map<String, String> recSection = new HashMap<>();
                recSection.put("content", recText.toString());
                elements.add(recSection);
            }

            // 5. 重点关注事项
            JsonNode focusItems = analysis.path("focus_items");
            if (focusItems.isArray() && focusItems.size() > 0) {
                StringBuilder focusText = new StringBuilder("**🎯 本次会议重点关注:**\n\n");
                for (JsonNode item : focusItems) {
                    focusText.append("- ").append(item.asText()).append("\n");
                }
                Map<String, String> focusSection = new HashMap<>();
                focusSection.put("content", focusText.toString());
                elements.add(focusSection);
            }

            log.info("Smart progress card elements built: {} sections", elements.size());

        } catch (Exception e) {
            log.error("Failed to parse AI analysis result: {}", e.getMessage());
            throw new RuntimeException("AI analysis parse failed", e);
        }

        return elements;
    }

    /**
     * 构建延期项详情文本（供AI分析）
     */
    private String buildDelayedItemsText(List<MeetingTodo> todos, int delayedCount) {
        if (delayedCount == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        List<MeetingTodo> delayedTodos = todos.stream()
                .filter(t -> TodoStatus.DELAYED.name().equals(t.getStatus()))
                .collect(Collectors.toList());

        for (MeetingTodo todo : delayedTodos) {
            text.append("- 内容: ").append(todo.getContent())
                    .append("\n  责任人: ").append(todo.getAssigneeName())
                    .append("\n  原因: ").append(todo.getBlockReason() != null ? todo.getBlockReason() : "未说明")
                    .append("\n  截止: ").append(todo.getDeadline() != null ? todo.getDeadline().toString() : "无")
                    .append("\n\n");
        }

        return text.toString();
    }

    /**
     * 降级方案：构建简单进度卡片（原有逻辑）
     */
    public List<Map<String, String>> buildFallbackCardElements(
            String previousTitle,
            int completed,
            int inProgress,
            int delayed,
            List<MeetingTodo> todos) {

        List<Map<String, String>> elements = new java.util.ArrayList<>();

        // 统计概述
        Map<String, String> summary = new HashMap<>();
        summary.put("content", String.format(
                "## 📊 上次会议待办进度\n\n**%s**\n\n- ✅ 已完成: %d\n- 🔄 进行中: %d\n- ⚠️ 已延期: %d",
                previousTitle, completed, inProgress, delayed));
        elements.add(summary);

        // 延期项高亮
        if (delayed > 0) {
            Map<String, String> delayedSection = new HashMap<>();
            StringBuilder delayedText = new StringBuilder("**⚠️ 延期项详情:**\n\n");
            for (MeetingTodo todo : todos.stream()
                    .filter(t -> TodoStatus.DELAYED.name().equals(t.getStatus()))
                    .collect(Collectors.toList())) {
                delayedText.append("- ").append(todo.getContent())
                        .append("（责任人: ").append(todo.getAssigneeName()).append("）\n");
            }
            delayedSection.put("content", delayedText.toString());
            elements.add(delayedSection);
        }

        return elements;
    }
}