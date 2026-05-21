package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenClaw MCP+Skill 方式的 AgentProvider 实现。
 *
 * <p>通过 OpenClaw Gateway HTTP API（{@code POST /api/v1/sessions/send}）与 Agent 交互，
 * 支持 Skill 模式（精简 prompt）和传统模式（完整 prompt）。
 *
 * <p>由配置 {@code openclaw.agent.provider=mcp} 激活。
 *
 * <p>Skill 模式下：
 * <ul>
 *   <li>prompt 仅包含 {@code /skill:xxx} 指令和业务数据，由 SKILL.md 定义工具调用步骤和输出格式</li>
 *   <li>飞书多维表数据由 MCP Server 直接读取，不调用 {@code BitableDirectiveBuilder}</li>
 * </ul>
 *
 * <p>传统模式下（{@code skillMode=false}）：
 * <ul>
 *   <li>拼装完整 prompt，兼容旧方案</li>
 *   <li>仍可调用 {@code BitableDirectiveBuilder} 构建多维表指令</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "openclaw.agent.provider", havingValue = "mcp")
public class OpenClawMcpProvider implements AgentProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openclaw.enabled:false}")
    private boolean enabled;

    @Value("${openclaw.gateway-url:}")
    private String gatewayUrl;

    @Value("${openclaw.agent-session-key:}")
    private String sessionKey;

    @Value("${openclaw.auth-token:}")
    private String authToken;

    @Value("${openclaw.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${openclaw.skill-mode:true}")
    private boolean skillMode;

    @Value("${openclaw.cli.agent-name:JQClaw}")
    private String agentName;

    public OpenClawMcpProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ========== analyzePreviousProgress ==========

    @Override
    public String analyzePreviousProgress(String meetingId,
                                          String previousMeetingId,
                                          String previousTitle,
                                          Map<String, Integer> todoStats,
                                          String delayedItems,
                                          String feishuMultitableDirective) {
        if (!isAvailable()) {
            log.info("OpenClaw MCP Provider disabled or misconfigured, skip progress analysis");
            return null;
        }

        String prompt;
        if (skillMode) {
            prompt = buildSkillPrompt("progress-analysis", map -> {
                map.put("meetingId", meetingId);
                map.put("previousMeetingId", previousMeetingId);
                map.put("previousTitle", previousTitle);
                map.put("delayed", String.valueOf(todoStats.getOrDefault("delayed", 0)));
                map.put("inProgress", String.valueOf(todoStats.getOrDefault("inProgress", 0)));
                map.put("completed", String.valueOf(todoStats.getOrDefault("completed", 0)));
                if (delayedItems != null && !delayedItems.isEmpty()) {
                    map.put("delayedItems", delayedItems);
                }
            });
        } else {
            prompt = buildFullProgressPrompt(previousMeetingId, previousTitle,
                    todoStats, delayedItems, feishuMultitableDirective);
        }

        return callGateway(prompt, "progress_analysis");
    }

    // ========== runMatterProgressReport ==========

    @Override
    public String runMatterProgressReport(Meeting meeting, String bitableDirective) {
        if (!isAvailable()) {
            log.info("OpenClaw MCP Provider disabled or misconfigured, skip matter progress");
            return null;
        }

        String prompt;
        if (skillMode) {
            prompt = buildSkillPrompt("matter-progress", map -> {
                map.put("meetingId", meeting.getId());
                map.put("title", meeting.getTitle());
                map.put("company", meeting.getCompany());
                map.put("groupName", meeting.getGroupName());
            });
        } else {
            if (bitableDirective == null || bitableDirective.isBlank()) {
                return null;
            }
            prompt = buildFullMatterProgressPrompt(meeting, bitableDirective);
        }

        return callGateway(prompt, "matter_progress");
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

        String result = callGateway(prompt, "minute_enhancement");
        return result != null ? result : rawMinute;
    }

    // ========== isAvailable ==========

    @Override
    public boolean isAvailable() {
        return enabled && gatewayUrl != null && !gatewayUrl.isBlank();
    }

    // ========== Gateway HTTP 调用 ==========

    /**
     * 通过 Gateway HTTP API 调用 Agent。
     *
     * @param prompt   发送给 Agent 的消息
     * @param taskType 任务类型（日志用）
     * @return Agent 回复文本；失败时返回 {@code null}
     */
    private String callGateway(String prompt, String taskType) {
        log.info("OpenClaw MCP call: taskType={}, skillMode={}, promptLength={}",
                taskType, skillMode, prompt.length());

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
            String endpoint = gatewayUrl.replaceAll("/$", "") + "/api/v1/sessions/send";

            ResponseEntity<String> resp = restTemplate.exchange(
                    endpoint, HttpMethod.POST, request, String.class);

            return parseAgentReply(resp.getBody(), taskType);

        } catch (Exception e) {
            log.error("OpenClaw MCP call failed: taskType={}, error={}", taskType, e.getMessage());
            return null;
        }
    }

    /**
     * 解析 Agent 回复，同时兼容 CLI JSON 和 HTTP API 两种格式。
     *
     * <ul>
     *   <li>CLI 格式：{@code {"status":"ok","result":{"payloads":[{"text":"..."}]}}}</li>
     *   <li>HTTP 格式：{@code {"reply":"..."}} / {@code {"content":"..."}} /
     *       {@code {"message":"..."}} / {@code {"response":"..."}}</li>
     * </ul>
     */
    private String parseAgentReply(String responseBody, String taskType) {
        if (responseBody == null || responseBody.isBlank()) {
            log.warn("OpenClaw MCP empty response: taskType={}", taskType);
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);

            // CLI 格式：status=ok + result.payloads[0].text
            if (json.has("status") && "ok".equals(json.path("status").asText())) {
                JsonNode payloads = json.path("result").path("payloads");
                if (payloads.isArray() && payloads.size() > 0) {
                    String text = payloads.get(0).path("text").asText();
                    log.info("OpenClaw MCP success (CLI format): taskType={}, replyLength={}",
                            taskType, text.length());
                    return text;
                }
            }

            // HTTP 格式：依次尝试 reply / content / message / response
            for (String field : new String[]{"reply", "content", "message", "response"}) {
                if (json.has(field) && !json.get(field).isNull()) {
                    String text = json.get(field).asText();
                    if (!text.isEmpty()) {
                        log.info("OpenClaw MCP success (HTTP format, field={}): taskType={}, replyLength={}",
                                field, taskType, text.length());
                        return text;
                    }
                }
            }

            // 嵌套在 data 字段中
            JsonNode data = json.path("data");
            if (!data.isMissingNode() && data.isObject()) {
                for (String field : new String[]{"reply", "content", "message", "response", "text"}) {
                    if (data.has(field) && !data.get(field).isNull()) {
                        String text = data.get(field).asText();
                        if (!text.isEmpty()) {
                            log.info("OpenClaw MCP success (data.{}): taskType={}, replyLength={}",
                                    field, taskType, text.length());
                            return text;
                        }
                    }
                }
            }

            log.warn("OpenClaw MCP unrecognized response format: taskType={}", taskType);
            return null;

        } catch (Exception e) {
            log.error("OpenClaw MCP response parse error: taskType={}, error={}", taskType, e.getMessage());
            return null;
        }
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

    private String buildFullProgressPrompt(String previousMeetingId, String previousTitle,
                                           Map<String, Integer> todoStats,
                                           String delayedItems,
                                           String feishuMultitableDirective) {
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

        return task.toString();
    }

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
}
