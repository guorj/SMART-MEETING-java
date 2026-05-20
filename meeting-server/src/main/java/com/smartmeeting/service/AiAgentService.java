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
 * OpenClaw AI Agent 调用门面（通过本地 {@code openclaw agent} CLI）。
 *
 * <p>介入场景：
 * <ul>
 *   <li>会议开始 — 上次待办进度 JSON 分析（{@link #analyzePreviousProgress}）</li>
 *   <li>录音页 — 综合管理会事项进度 Markdown（{@link #runMatterProgressReportViaOpenclaw}）</li>
 *   <li>纪要生成 — 初版纪要质量优化（{@link #enhanceMeetingMinutes}）</li>
 * </ul>
 *
 * <p>由配置 {@code openclaw.enabled} 总开关控制；关闭时各方法快速返回 {@code null} 或原文。
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

    /**
     * @param restTemplate HTTP 客户端（保留用于扩展网关调用）
     */
    public AiAgentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 环节1：分析上次会议待办进度，生成结构化 JSON 洞察。
     *
     * @param meetingId                 当前会议 ID
     * @param previousMeetingId         上次会议 ID
     * @param previousTitle             上次会议标题
     * @param todoStats                 待办统计（键如 completed、inProgress、delayed）
     * @param delayedItems              延期项详情文本
     * @param feishuMultitableDirective 非空时置于任务最前，要求 Agent 读取飞书多维表后与 DB 统计交叉分析
     * @return Agent 回复文本（通常为 JSON）；未启用或调用失败时返回 {@code null}
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
     * 录音页「事项进度通报」：由 OpenClaw 读飞书多维表后直接输出 Markdown（不经 meeting.llm）。
     *
     * @param meeting          当前会议
     * @param bitableDirective 多维表读取指令（来自 {@link OpenclawComprehensiveBitableBranch}）
     * @return Markdown 正文；未启用、指令为空或调用失败时返回 {@code null}
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
     * @param transcriptText 转写原文片段（可选，用于校验）
     * @return Agent 返回的 JSON 字符串；未启用时返回 {@code rawMinute}
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
     * 通过 {@code openclaw agent} 子进程同步调用 Agent。
     *
     * @param prompt   完整任务提示词
     * @param taskType 任务类型标识（仅用于日志）
     * @return 解析后的回复文本；超时、非零退出或解析失败时返回 {@code null}
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
     * 从 CLI {@code --json} 标准输出中提取 payloads[0].text。
     *
     * @param jsonOutput CLI 标准输出
     * @param taskType   任务类型（日志）
     * @return 文本内容；status 非 ok 时返回 {@code null}
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
     * 解析 HTTP 网关式 Agent 回复（兼容 reply/content/message/response 字段或纯文本）。
     *
     * @param responseBody 原始响应体
     * @return 提取的文本内容
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
     * 异步包装 {@link #callAgent}（不阻塞调用线程）。
     *
     * @param prompt   任务提示词
     * @param taskType 任务类型
     * @return 已完成 Future，值为 Agent 回复或 {@code null}
     */
    @org.springframework.scheduling.annotation.Async
    public CompletableFuture<String> callAgentAsync(String prompt, String taskType) {
        return CompletableFuture.completedFuture(callAgent(prompt, taskType));
    }

    /** 将预设类型编码转为中文展示名。 */
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
     * 检查 Agent 是否已配置为可用（当前仅反映 {@code openclaw.enabled}）。
     *
     * @return 开关开启时为 {@code true}
     */
    public boolean isAgentAvailable() {
        if (!enabled) {
            return false;
        }
        // 可选：发送测试请求检查连通性
        return true;
    }
}