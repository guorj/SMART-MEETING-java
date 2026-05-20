package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * OpenClaw AI Agent 调用门面 — 通过 Gateway HTTP API 调用 Agent，支持 MCP + Skill 模式。
 *
 * <p>介入场景：
 * <ul>
 *   <li>会议开始 — 上次待办进度 JSON 分析（{@link #analyzePreviousProgress}）</li>
 *   <li>录音页 — 综合管理会事项进度 Markdown（{@link #runMatterProgressReportViaOpenclaw}）</li>
 *   <li>纪要生成 — 初版纪要质量优化（{@link #enhanceMeetingMinutes}）</li>
 * </ul>
 *
 * <p>{@code skill-mode=true} 时 prompt 仅传业务数据，输出格式与 MCP 工具步骤由 {@code skills/} 定义；
 * 由配置 {@code openclaw.enabled} 总开关控制，关闭时各方法快速返回 {@code null} 或原文。
 */
@Slf4j
@Service
public class AiAgentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openclaw.gateway-url:http://127.0.0.1:18792}")
    private String gatewayUrl;

    @Value("${openclaw.agent-session-key:agent:jqclaw:direct:ou_5eadac907bd2dc79e8b39205ef6bf33a}")
    private String sessionKey;

    @Value("${openclaw.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${openclaw.enabled:false}")
    private boolean enabled;

    @Value("${openclaw.auth-token:}")
    private String authToken;

    /** 传输方式：http（推荐）或 cli（旧方案） */
    @Value("${openclaw.transport:http}")
    private String transport;

    /** CLI 专属配置 */
    @Value("${openclaw.cli-profile:clone-boss}")
    private String cliProfile;

    @Value("${openclaw.cli-state-dir:/home/alan/.openclaw-clone-boss}")
    private String cliStateDir;

    /** Skill 触发模式：通过 /skill 端点按需加载，而非在 prompt 中描述完整指令 */
    @Value("${openclaw.skill-mode:true}")
    private boolean skillMode;

    /**
     * @param restTemplate HTTP 客户端，用于 Gateway {@code /api/v1/sessions/send}
     */
    public AiAgentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 环节1：分析上次会议待办进度
     *
     * <p>Skill 模式（{@code skillMode=true}）下仅传递业务数据，
     * 输出格式与工具调用步骤由 {@code skills/progress-analysis/SKILL.md} 定义，不占用 prompt token。
     * <p>兼容模式（{@code skillMode=false}）下仍拼接完整 prompt（旧方案）。
     *
     * @param meetingId 当前会议ID
     * @param previousMeetingId 上次会议ID
     * @param previousTitle 上次会议标题
     * @param todoStats 待办统计（完成数、进行中数、延期数）
     * @param delayedItems 延期项详情列表
     * @param feishuMultitableDirective 非空时附加在任务最前（Skill 模式下改为 MCP 工具参数，此参数将逐步弃用）
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

        if (skillMode) {
            // Skill 模式：仅传业务数据，Skill 定义格式和工具调用步骤
            StringBuilder task = new StringBuilder();
            task.append("/skill:progress-analysis\n\n");
            task.append("meetingId=").append(meetingId).append("\n");
            task.append("previousMeetingId=").append(previousMeetingId).append("\n");
            task.append("previousTitle=").append(previousTitle).append("\n");
            task.append("delayed=").append(todoStats.getOrDefault("delayed", 0)).append("\n");
            task.append("inProgress=").append(todoStats.getOrDefault("inProgress", 0)).append("\n");
            task.append("completed=").append(todoStats.getOrDefault("completed", 0)).append("\n");
            if (delayedItems != null && !delayedItems.isEmpty()) {
                task.append("delayedItems:\n").append(delayedItems).append("\n");
            }
            return callAgent(task.toString(), "progress_analysis");
        }

        // 兼容模式：完整 prompt（旧方案）
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
     * 录音页「事项进度通报」 — Skill 模式下由 {@code skills/matter-progress/SKILL.md} 定义流程与输出格式。
     *
     * @param meeting 当前会议
     * @param bitableDirective 多维表读取指令（兼容模式下来自 {@link OpenclawComprehensiveBitableBranch}）
     * @return Markdown 正文；未启用、指令为空或调用失败时返回 {@code null}
     */
    public String runMatterProgressReportViaOpenclaw(Meeting meeting, String bitableDirective) {
        if (!enabled) {
            log.info("AI Agent disabled, skip matter progress via Openclaw");
            return null;
        }

        if (skillMode) {
            StringBuilder task = new StringBuilder();
            task.append("/skill:matter-progress\n\n");
            task.append("meetingId=").append(meeting.getId()).append("\n");
            task.append("title=").append(meeting.getTitle()).append("\n");
            task.append("company=").append(meeting.getCompany()).append("\n");
            task.append("groupName=").append(meeting.getGroupName()).append("\n");
            return callAgent(task.toString(), "matter_progress_bitable");
        }

        // 兼容模式（旧方案）
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
     * 环节2：优化会议纪要 — Skill 模式下由 {@code skills/minute-enhancement/SKILL.md} 定义流程与输出格式。
     *
     * @param meetingId 会议ID
     * @param rawMinute LLM生成的初版纪要
     * @param meetingTitle 会议主题
     * @param meetingType 会议类型（1-6）
     * @param participants 参会人列表
     * @param transcriptText 转写原文（可选，用于校验）
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
            return rawMinute;
        }

        if (skillMode) {
            StringBuilder task = new StringBuilder();
            task.append("/skill:minute-enhancement\n\n");
            task.append("meetingId=").append(meetingId).append("\n");
            task.append("title=").append(meetingTitle).append("\n");
            task.append("meetingType=").append(getMeetingTypeName(meetingType)).append("\n");
            task.append("participants=").append(participants != null ? participants : "未知").append("\n");
            task.append("rawMinute:\n").append(rawMinute).append("\n");
            if (transcriptText != null && transcriptText.length() > 100) {
                task.append("transcriptExcerpt:\n")
                    .append(transcriptText.substring(0, Math.min(2000, transcriptText.length()))).append("\n");
            }
            return callAgent(task.toString(), "minute_enhancement");
        }

        // 兼容模式：完整 prompt（旧方案）
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
     * 调用 OpenClaw Agent — 通过 Gateway HTTP API（替代 CLI ProcessBuilder）
     *
     * <p>HTTP 方式省去每次 fork 子进程（Node.js 加载 + CLI 初始化 ~3-5s），
     * Agent 会话在 Gateway 侧保持常驻，MCP 工具连接同样由 Gateway 维护。
     *
     * @param prompt 任务描述；若以 "/skill:" 开头则触发 Skill 热加载（Phase 4）
     * @param taskType 任务类型（用于日志）
     * @return 解析后的回复文本；超时或调用失败时返回 {@code null}
     */
    private String callAgent(String prompt, String taskType) {
        log.info("调用AI Agent: taskType={}, promptLength={}, mode=http", taskType, prompt.length());

        if (!enabled) {
            log.info("AI Agent disabled, skip call");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", "Bearer " + authToken);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionKey", sessionKey);
            body.put("message", prompt);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String endpoint = gatewayUrl + "/api/v1/sessions/send";

            ResponseEntity<String> resp = restTemplate.exchange(
                    endpoint, HttpMethod.POST, request, String.class);

            String responseBody = resp.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.warn("AI Agent返回空响应: taskType={}", taskType);
                return null;
            }

            return parseAgentReply(responseBody, taskType);

        } catch (Exception e) {
            log.error("AI Agent调用异常(HTTP): taskType={}, error={}", taskType, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 Agent 回复 — 支持 CLI JSON 格式与 HTTP API 格式
     *
     * <p>CLI 格式: {"status":"ok","result":{"payloads":[{"text":"..."}]}}
     * <p>HTTP 格式: {@code reply} / {@code content} / {@code message} / {@code response} 或直接文本
     *
     * @param responseBody 原始响应体
     * @param taskType     任务类型（日志）
     * @return 提取的文本内容；status 非 ok 且无已知字段时返回 {@code null} 或原文兜底
     */
    private String parseAgentReply(String responseBody, String taskType) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);

            // CLI 格式兼容（过渡期可能仍返回此格式）
            if (json.has("status") && json.path("status").asText().equals("ok")) {
                JsonNode payloads = json.path("result").path("payloads");
                if (payloads.isArray() && payloads.size() > 0) {
                    String text = payloads.get(0).path("text").asText();
                    log.info("AI Agent(CLI格式)回复成功: taskType={}, replyLength={}", taskType, text.length());
                    return text;
                }
            }

            // HTTP API 格式
            String[] fields = {"reply", "content", "message", "response"};
            for (String field : fields) {
                if (json.has(field)) {
                    String text = json.get(field).asText();
                    log.info("AI Agent(HTTP格式)回复成功: taskType={}, field={}, replyLength={}",
                            taskType, field, text.length());
                    return text;
                }
            }

            // 兜底：整段作为文本
            log.warn("AI Agent回复格式未知: taskType={}, rawLength={}", taskType, responseBody.length());
            return responseBody;

        } catch (Exception e) {
            // 非 JSON，直接返回文本
            log.info("AI Agent回复为非JSON文本: taskType={}, length={}", taskType, responseBody.length());
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