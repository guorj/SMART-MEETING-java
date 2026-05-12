package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 服务 - 调用 OpenClaw Agent（小栈）进行智能分析
 *
 * 介入场景：
 * 1. 会议开始：上次会议待办进度深度分析
 * 2. 会议结束：纪要质量优化增强
 */
@Slf4j
@Service
public class AiAgentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openclaw.gateway-url:http://127.0.0.1:18789}")
    private String gatewayUrl;

    @Value("${openclaw.agent-session-key:agent:jqclaw:direct:ou_5eadac907bd2dc79e8b39205ef6bf33a}")
    private String sessionKey;

    @Value("${openclaw.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${openclaw.enabled:false}")
    private boolean enabled;

    @Value("${openclaw.auth-token:}")
    private String authToken;

    public AiAgentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 环节1：分析上次会议待办进度
     *
     * @param meetingId 当前会议ID
     * @param previousMeetingId 上次会议ID
     * @param previousTitle 上次会议标题
     * @param todoStats 待办统计（完成数、进行中数、延期数）
     * @param delayedItems 延期项详情列表
     * @return 智能分析报告（JSON格式）
     */
    /**
     * @param feishuMultitableDirective 非空时附加在任务最前：要求 Agent/CLI 读取指定飞书多维表格后再与 DB 待办统计交叉分析（临时业务分支）
     */
    public String analyzePreviousProgress(
            String meetingId,
            String previousMeetingId,
            String previousTitle,
            Map<String, Integer> todoStats,
            String delayedItems,
            String feishuMultitableDirective) {

        if (!enabled) {
            log.info("AI Agent disabled, skip progress analysis");
            return null;
        }

        // 构建任务描述
        StringBuilder task = new StringBuilder();
        task.append("【任务】分析上次会议待办进度，给出智能洞察和建议\n\n");
        if (feishuMultitableDirective != null && !feishuMultitableDirective.isBlank()) {
            task.append(feishuMultitableDirective.trim()).append("\n\n");
        }
        task.append("上次会议：").append(previousTitle).append("\n");
        task.append("会议ID：").append(previousMeetingId).append("\n\n");

        task.append("待办统计：\n");
        task.append("- ⚠️ 已延期: ").append(todoStats.getOrDefault("delayed", 0)).append("项\n\n");
        task.append("- 🔄 进行中: ").append(todoStats.getOrDefault("inProgress", 0)).append("项\n");
        task.append("- ✅ 已完成: ").append(todoStats.getOrDefault("completed", 0)).append("项\n");

        if (delayedItems != null && !delayedItems.isEmpty()) {
            task.append("延期项详情：\n").append(delayedItems).append("\n\n");
        }

        task.append("请分析并输出以下内容（JSON格式）：\n");
        task.append("1. progress_summary: 进度概述（2-3句话）\n");
        task.append("2. delay_reasons: 延期原因归类分析\n");
        task.append("3. high_priority_alerts: 高优先级待办提醒\n");
        task.append("4. recommendations: 本次会议推进建议\n");
        task.append("5. focus_items: 本次会议重点关注事项\n\n");
        task.append("输出格式要求：\n");
        task.append("{\n");
        task.append("  \"progress_summary\": \"...\",\n");
        task.append("  \"delay_reasons\": [{\"category\": \"...\", \"count\": N, \"detail\": \"...\"}],\n");
        task.append("  \"high_priority_alerts\": [{\"content\": \"...\", \"assignee\": \"...\"}],\n");
        task.append("  \"recommendations\": [\"建议1\", \"建议2\"],\n");
        task.append("  \"focus_items\": [\"关注项1\", \"关注项2\"]\n");
        task.append("}");

        return callAgent(task.toString(), "progress_analysis");
    }

    /**
     * 录音页「事项进度通报」临时路径：OpenClaw CLI 读飞书多维表后直接输出 Markdown（不经 meeting.llm）。
     */
    public String runMatterProgressReportViaOpenclaw(Meeting meeting, String bitableDirective) {
        if (!enabled) {
            log.info("AI Agent disabled, skip matter progress via Openclaw");
            return null;
        }
        if (bitableDirective == null || bitableDirective.isBlank()) {
            return null;
        }
        StringBuilder task = new StringBuilder();
        task.append(bitableDirective.trim()).append("\n\n");
        task.append("## 当前会议上下文（供你写通报时引用）\n");
        task.append("- 会议ID：").append(meeting.getId()).append("\n");
        task.append("- 主题：").append(meeting.getTitle()).append("\n");
        task.append("- 集团/会议组：").append(meeting.getCompany()).append(" / ").append(meeting.getGroupName()).append("\n\n");
        task.append("请严格按上文「输出要求」生成事项进度通报。");
        return callAgent(task.toString(), "matter_progress_bitable");
    }

    /**
     * 环节2：优化会议纪要
     *
     * @param meetingId 会议ID
     * @param rawMinute LLM生成的初版纪要
     * @param meetingTitle 会议主题
     * @param meetingType 会议类型（1-6）
     * @param participants 参会人列表
     * @param transcriptText 转写原文（可选，用于校验）
     * @return 优化后的纪要 + 质量检查报告
     */
    public String enhanceMeetingMinutes(
            String meetingId,
            String rawMinute,
            String meetingTitle,
            Integer meetingType,
            String participants,
            String transcriptText) {

        if (!enabled) {
            log.info("AI Agent disabled, skip minute enhancement");
            return rawMinute; // 返回原纪要
        }

        // 构建任务描述
        StringBuilder task = new StringBuilder();
        task.append("【任务】优化会议纪要，提升质量和针对性\n\n");

        task.append("会议信息：\n");
        task.append("- 主题：").append(meetingTitle).append("\n");
        task.append("- 类型：").append(getMeetingTypeName(meetingType)).append("\n");
        task.append("- 参会人：").append(participants != null ? participants : "未知").append("\n\n");

        task.append("LLM初版纪要：\n");
        task.append("---\n");
        task.append(rawMinute);
        task.append("\n---\n\n");

        // 转写原文（用于校验关键信息）
        if (transcriptText != null && transcriptText.length() > 100) {
            task.append("转写原文片段（用于校验）：\n");
            task.append(transcriptText.substring(0, Math.min(2000, transcriptText.length())));
            task.append("\n\n");
        }

        task.append("请优化纪要并输出以下内容（JSON格式）：\n");
        task.append("1. optimized_minute: 优化后的纪要正文\n");
        task.append("2. quality_check: 质量检查报告\n");
        task.append("3. missing_info: 缺失的关键信息\n");
        task.append("4. speaker_summary: 各发言人发言要点归类\n");
        task.append("5. todo_highlight: 重点待办标注\n\n");

        task.append("输出格式要求：\n");
        task.append("{\n");
        task.append("  \"optimized_minute\": \"优化后的完整纪要\",\n");
        task.append("  \"quality_check\": {\n");
        task.append("    \"score\": 85,\n");
        task.append("    \"issues\": [\"待办责任人未明确\", \"决议事项缺少截止日期\"]\n");
        task.append("  },\n");
        task.append("  \"missing_info\": [\"缺失信息1\"],\n");
        task.append("  \"speaker_summary\": [{\"speaker\": \"张三\", \"key_points\": [\"要点1\"]}],\n");
        task.append("  \"todo_highlight\": [{\"content\": \"待办\", \"priority\": \"HIGH\", \"assignee\": \"张三\"}]\n");
        task.append("}");

        return callAgent(task.toString(), "minute_enhancement");
    }

    /**
     * 调用 OpenClaw Agent（通过CLI命令）
     *
     * @param prompt 任务描述
     * @param taskType 任务类型（用于日志）
     * @return Agent回复
     */
    private String callAgent(String prompt, String taskType) {
        log.info("调用AI Agent: taskType={}, promptLength={}", taskType, prompt.length());

        if (!enabled) {
            log.info("AI Agent disabled, skip call");
            return null;
        }

        try {
            // 使用ProcessBuilder调用openclaw CLI命令
            ProcessBuilder pb = new ProcessBuilder(
                "openclaw", "agent",
                "--agent", "JQClaw",
                "--message", prompt,
                "--timeout", String.valueOf(timeoutSeconds),
                "--json"
            );
            pb.redirectErrorStream(true);
            pb.directory(new java.io.File(System.getProperty("user.home")));
            
            // 设置环境变量，确保使用clone-boss配置
            java.util.Map<String, String> env = pb.environment();
            env.put("OPENCLAW_PROFILE", "clone-boss");
            env.put("OPENCLAW_STATE_DIR", "/home/alan/.openclaw-clone-boss");

            Process process = pb.start();

            // 等待执行完成
            boolean finished = process.waitFor(timeoutSeconds + 10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                log.warn("AI Agent调用超时: taskType={}", taskType);
                return null;
            }

            // 读取输出
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            
            if (process.exitValue() != 0) {
                log.warn("AI Agent调用失败: exitCode={}, output={}", process.exitValue(), output);
                return null;
            }

            // 解析JSON响应
            return parseCliJsonResponse(output, taskType);

        } catch (Exception e) {
            log.error("AI Agent调用异常: taskType={}, error={}", taskType, e.getMessage());
            return null;
        }
    }

    /**
     * 解析CLI JSON响应
     */
    private String parseCliJsonResponse(String jsonOutput, String taskType) {
        try {
            JsonNode json = objectMapper.readTree(jsonOutput);

            if (json.path("status").asText().equals("ok")) {
                JsonNode payloads = json.path("result").path("payloads");
                if (payloads.isArray() && payloads.size() > 0) {
                    String text = payloads.get(0).path("text").asText();
                    log.info("AI Agent回复成功: taskType={}, replyLength={}", taskType, text.length());
                    return text;
                }
            }

            log.warn("AI Agent返回非成功状态: status={}", json.path("status").asText());
            return null;

        } catch (Exception e) {
            log.error("CLI响应解析失败: error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 Agent 回复
     * OpenClaw sessions_send 返回格式可能是：
     * {"reply": "内容"} 或 {"content": "内容"} 或直接是内容
     */
    private String parseAgentReply(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            // 尝试多种字段名
            if (json.has("reply")) {
                return json.get("reply").asText();
            }
            if (json.has("content")) {
                return json.get("content").asText();
            }
            if (json.has("message")) {
                return json.get("message").asText();
            }
            if (json.has("response")) {
                return json.get("response").asText();
            }

            // 如果都不是，可能直接是文本
            return responseBody;

        } catch (Exception e) {
            // 不是JSON，直接返回文本
            return responseBody;
        }
    }

    /**
     * 异步调用（不阻塞主流程）
     */
    @org.springframework.scheduling.annotation.Async
    public CompletableFuture<String> callAgentAsync(String prompt, String taskType) {
        return CompletableFuture.completedFuture(callAgent(prompt, taskType));
    }

    /**
     * 获取会议类型名称
     */
    private String getMeetingTypeName(Integer typeCode) {
        if (typeCode == null) {
            return "其他会议";
        }
        return switch (typeCode) {
            case 1 -> "综合管理会";
            case 2 -> "技术委员会";
            case 3 -> "市场经营会";
            case 4 -> "财务月会";
            case 5 -> "经营委员会";
            case 6 -> "其他会议";
            default -> "未知类型";
        };
    }

    /**
     * 检查 Agent 是否可用
     */
    public boolean isAgentAvailable() {
        if (!enabled) {
            return false;
        }
        // 可选：发送测试请求检查连通性
        return true;
    }
}