package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.smartmeeting.service.host.AgendaBriefingMarkdownValidator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * OpenClaw MCP+Skill 方式的 AgentProvider 实现。
 *
 * <p>通过 OpenClaw Gateway WebSocket RPC（{@code chat.send}）与 Agent 交互，
 * 支持 Skill 模式（精简 prompt）和传统模式（完整 prompt）。
 *
 * <p>由配置 {@code openclaw.agent.provider=mcp} 激活。
 *
 * <p>Skill 模式下：
 * <ul>
 *   <li>prompt 仅包含 {@code /skill:xxx} 指令和业务数据，由 SKILL.md 定义工具调用步骤和输出格式</li>
 *   <li>飞书多维表数据由 MCP Server 直接读取</li>
 * </ul>
 *
 * <p>会前进度（上次待办进度卡片 + progress-analysis Skill）已在 v0.9 下线，
 * 由 feishu-scheduled-bot + matter-progress-core 的「会前事项对比通报」取代。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "openclaw.agent.provider", havingValue = "mcp")
public class OpenClawMcpProvider implements AgentProvider {

    /**
     * 全局串行：避免会序通报与纪要增强并发占用同一 Gateway 会话导致返回串台。
     */
    private static final Semaphore GATEWAY_INVOKE_SEMAPHORE = new Semaphore(1, true);

    private final OpenClawGatewayWsClient gatewayWsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openclaw.enabled:false}")
    private boolean enabled;

    @Value("${openclaw.gateway-url:}")
    private String gatewayUrl;

    @Value("${openclaw.agent-session-key:}")
    private String sessionKey;

    @Value("${openclaw.auth-token:}")
    private String authToken;

    /** 可选：已配对设备的 deviceToken，用于跨机 WebSocket 写入（否则请用 127.0.0.1 Gateway） */
    @Value("${openclaw.device-token:}")
    private String deviceToken;

    @Value("${openclaw.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${openclaw.skill-mode:true}")
    private boolean skillMode;

    public OpenClawMcpProvider(OpenClawGatewayWsClient gatewayWsClient) {
        this.gatewayWsClient = gatewayWsClient;
    }

    // ========== runMatterProgressReport ==========

    @Override
    public String runMatterProgressReport(Meeting meeting, String bitableDirective) {
        return runMatterProgressReport(meeting, bitableDirective, null, null);
    }

    @Override
    public String runMatterProgressReport(Meeting meeting,
                                          String bitableDirective,
                                          String feishuUrl,
                                          String agendaTitle) {
        return runMatterProgressReport(meeting, bitableDirective, feishuUrl, agendaTitle, -1, null);
    }

    @Override
    public String runMatterProgressReport(Meeting meeting,
                                          String bitableDirective,
                                          String feishuUrl,
                                          String agendaTitle,
                                          int agendaIndex,
                                          String requestId) {
        String taskId = requestId != null && !requestId.isBlank()
                ? OpenClawTaskIds.briefing(meeting.getId(), agendaIndex >= 0 ? agendaIndex : 0,
                parseGenerationFromRequestId(requestId))
                : OpenClawTaskIds.briefing(meeting.getId(), agendaIndex >= 0 ? agendaIndex : 0, 0);
        return runMatterProgressReport(meeting, bitableDirective, feishuUrl, agendaTitle, agendaIndex, requestId, taskId);
    }

    @Override
    public String runMatterProgressReport(Meeting meeting,
                                          String bitableDirective,
                                          String feishuUrl,
                                          String agendaTitle,
                                          int agendaIndex,
                                          String requestId,
                                          String taskId) {
        if (!isAvailable()) {
            log.info("OpenClaw MCP Provider disabled or misconfigured, skip matter progress");
            return null;
        }

        String effectiveTaskId = taskId != null && !taskId.isBlank()
                ? taskId.trim()
                : OpenClawTaskIds.briefing(meeting.getId(), Math.max(0, agendaIndex), 0);
        String briefingSessionKey = resolveSessionKeyForTask(effectiveTaskId, sessionKey);
        String prompt;
        if (skillMode) {
            prompt = buildSkillPrompt("matter-progress", map -> {
                map.put("taskId", effectiveTaskId);
                map.put("meetingId", meeting.getId());
                map.put("title", meeting.getTitle() != null ? meeting.getTitle() : "");
                map.put("company", meeting.getCompany() != null ? meeting.getCompany() : "");
                map.put("groupName", meeting.getGroupName() != null ? meeting.getGroupName() : "");
                if (bitableDirective != null && !bitableDirective.isBlank()) {
                    map.put("bitableHint", bitableDirective.trim());
                }
                if (feishuUrl != null && !feishuUrl.isBlank()) {
                    map.put("feishuUrl", feishuUrl.trim());
                }
                if (agendaTitle != null && !agendaTitle.isBlank()) {
                    map.put("agendaTitle", agendaTitle.trim());
                }
                if (agendaIndex >= 0) {
                    map.put("agendaIndex", String.valueOf(agendaIndex));
                }
                if (requestId != null && !requestId.isBlank()) {
                    map.put("requestId", requestId.trim());
                }
            });
        } else {
            if (bitableDirective == null || bitableDirective.isBlank()) {
                return null;
            }
            prompt = buildFullMatterProgressPrompt(meeting, bitableDirective);
        }

        return callGateway(prompt, OpenClawTaskIds.TASK_MATTER_PROGRESS, briefingSessionKey, effectiveTaskId);
    }

    /**
     * 每个 taskId 独立 sessionKey，避免 Gateway 把纪要增强结果复用到会序通报会话。
     */
    static String resolveSessionKeyForTask(String taskId, String baseSessionKey) {
        String base = baseSessionKey != null && !baseSessionKey.isBlank()
                ? baseSessionKey.trim()
                : "agent:openclaw";
        String tid = taskId != null ? taskId.trim() : "unknown";
        if (tid.length() > 120) {
            tid = tid.substring(0, 120);
        }
        return base + ":task:" + tid;
    }

    private static int parseGenerationFromRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return 0;
        }
        int last = requestId.lastIndexOf('-');
        if (last < 0 || last >= requestId.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(requestId.substring(last + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ========== enhanceMeetingMinutes ==========

    @Override
    public String enhanceMeetingMinutes(String meetingId,
                                        String rawMinute,
                                        String meetingTitle,
                                        Integer meetingType,
                                        String participants,
                                        String transcriptText) {
        if (!isAvailable()) {
            log.info("OpenClaw MCP Provider disabled or misconfigured, skip minute enhancement");
            return rawMinute;
        }

        String prompt;
        if (skillMode) {
            prompt = buildSkillPrompt("minute-enhancement", map -> {
                map.put("meetingId", meetingId);
                map.put("meetingTitle", meetingTitle);
                map.put("meetingType", String.valueOf(meetingType));
                map.put("participants", participants != null ? participants : "未知");
                map.put("rawMinuteLength", String.valueOf(rawMinute.length()));
            });
            // 纪要正文和转写原文太长，不适合放在 key=value 中；拼在 Skill 指令后面
            prompt += "\n\n---\n初版纪要：\n" + rawMinute;
            if (transcriptText != null && transcriptText.length() > 100) {
                prompt += "\n\n转写原文片段：\n"
                        + transcriptText.substring(0, Math.min(2000, transcriptText.length()));
            }
        } else {
            prompt = buildFullMinutePrompt(meetingTitle, meetingType, participants,
                    rawMinute, transcriptText);
        }

        String taskId = OpenClawTaskIds.minuteEnhancement(meetingId, System.nanoTime());
        String minuteSessionKey = resolveSessionKeyForTask(taskId, sessionKey);
        String result = callGateway(prompt, OpenClawTaskIds.TASK_MINUTE_ENHANCEMENT, minuteSessionKey, taskId);
        return result != null ? result : rawMinute;
    }

    // ========== isAvailable ==========

    @Override
    public boolean isAvailable() {
        boolean hasAuth = (authToken != null && !authToken.isBlank())
                || (deviceToken != null && !deviceToken.isBlank());
        return enabled && gatewayUrl != null && !gatewayUrl.isBlank() && hasAuth;
    }

    // ========== Gateway HTTP 调用 ==========

    /**
     * 通过 Gateway HTTP API 调用 Agent。
     *
     * @param prompt   发送给 Agent 的消息
     * @param taskType 任务类型（日志用）
     * @return Agent 回复文本；失败时返回 {@code null}
     */
    private String callGateway(String prompt, String taskType, String effectiveSessionKey) {
        return callGateway(prompt, taskType, effectiveSessionKey, null);
    }

    private String callGateway(String prompt, String taskType, String effectiveSessionKey, String taskId) {
        int effectiveTimeout = effectiveTimeoutSeconds(taskType);
        String keyForLog = effectiveSessionKey != null && effectiveSessionKey.length() > 48
                ? effectiveSessionKey.substring(0, 48) + "…"
                : effectiveSessionKey;
        log.info("OpenClaw MCP call: taskType={}, taskId={}, skillMode={}, gateway={}, sessionKey={}, promptLength={}, timeoutSec={}",
                taskType, taskId, skillMode, gatewayUrl, keyForLog, prompt.length(), effectiveTimeout);

        String body;
        long gatewayMs;
        try {
            GATEWAY_INVOKE_SEMAPHORE.acquire();
            try {
                long t0 = System.currentTimeMillis();
                body = gatewayWsClient.sendChatMessage(
                        gatewayUrl, authToken, deviceToken, effectiveSessionKey, prompt, effectiveTimeout, taskId);
                gatewayMs = System.currentTimeMillis() - t0;
            } finally {
                GATEWAY_INVOKE_SEMAPHORE.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OpenClaw MCP interrupted: taskType={}, taskId={}", taskType, taskId);
            return null;
        }

        if (body == null || body.isBlank()) {
            log.warn("OpenClaw MCP empty/failed response: taskType={}, taskId={}, gatewayMs={}",
                    taskType, taskId, gatewayMs);
            return null;
        }
        if (OpenClawTaskIds.TASK_MATTER_PROGRESS.equals(taskType)
                && AgendaBriefingMarkdownValidator.rejectReason(body.trim()) != null) {
            log.warn("OpenClaw MCP raw body rejected (task mismatch): taskType={}, taskId={}, reason={}",
                    taskType, taskId, AgendaBriefingMarkdownValidator.rejectReason(body.trim()));
            return null;
        }
        String markdown = OpenClawReplyExtractor.extractFromBody(body);
        if (markdown == null || markdown.isBlank()) {
            log.warn("OpenClaw MCP could not extract text: taskType={}, taskId={}, gatewayMs={}, bodyPrefix={}",
                    taskType, taskId, gatewayMs, body.length() > 120 ? body.substring(0, 120) + "…" : body);
            return null;
        }

        if (OpenClawTaskIds.TASK_MATTER_PROGRESS.equals(taskType)
                && AgendaBriefingMarkdownValidator.rejectReason(markdown) != null) {
            String reason = AgendaBriefingMarkdownValidator.rejectReason(markdown);
            log.warn("OpenClaw MCP response rejected (task mismatch): expected={}, taskId={}, reason={}, replyPrefix={}",
                    taskType, taskId, reason,
                    markdown.length() > 200 ? markdown.substring(0, 200) + "…" : markdown);
            return null;
        }

        log.info("OpenClaw MCP success: taskType={}, taskId={}, gatewayMs={}, replyLength={}",
                taskType, taskId, gatewayMs, markdown.length());
        return markdown;
    }

    /** matter_progress 使用全局 OpenClaw 超时。 */
    private int effectiveTimeoutSeconds(String taskType) {
        return timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    // ========== Skill prompt 构建 ==========

    /**
     * 构建 Skill 模式下的精简 prompt。
     *
     * <p>格式：{@code /skill:xxx\nkey=value\n...}
     */
    private String buildSkillPrompt(String skillName, java.util.function.Consumer<java.util.LinkedHashMap<String, String>> dataConsumer) {
        LinkedHashMap<String, String> data = new LinkedHashMap<>();
        dataConsumer.accept(data);

        StringBuilder sb = new StringBuilder();
        sb.append("/skill:").append(skillName);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            sb.append("\n").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    // ========== 完整 prompt 构建（skillMode=false 兼容模式） ==========

    private String buildFullMatterProgressPrompt(Meeting meeting, String bitableDirective) {
        StringBuilder task = new StringBuilder();
        task.append(bitableDirective.trim()).append("\n\n");
        task.append("## 当前会议上下文（供你写通报时引用）\n");
        task.append("- 会议ID：").append(meeting.getId()).append("\n");
        task.append("- 主题：").append(meeting.getTitle()).append("\n");
        task.append("- 集团/会议组：").append(meeting.getCompany()).append(" / ").append(meeting.getGroupName()).append("\n\n");
        task.append("请严格按上文「输出要求」生成事项进度通报。");
        return task.toString();
    }

    private String buildFullMinutePrompt(String meetingTitle, Integer meetingType,
                                         String participants, String rawMinute,
                                         String transcriptText) {
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

        return task.toString();
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
