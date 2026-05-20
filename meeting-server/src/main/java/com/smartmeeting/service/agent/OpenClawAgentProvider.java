package com.smartmeeting.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenClaw CLI 方式的 AgentProvider 实现。
 *
 * <p>通过 {@code ProcessBuilder} 调用本地 {@code openclaw agent} 子进程，
 * 将任务提示词以 CLI 参数传入，解析 JSON 标准输出。
 *
 * <p>由配置 {@code openclaw.agent.provider=openclaw} 激活。
 * 硬编码值已移入 {@code openclaw.cli.*} 配置项。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "openclaw.agent.provider", havingValue = "openclaw")
public class OpenClawAgentProvider implements AgentProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openclaw.enabled:false}")
    private boolean enabled;

    @Value("${openclaw.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${openclaw.cli.agent-name:JQClaw}")
    private String agentName;

    @Value("${openclaw.cli.profile:clone-boss}")
    private String cliProfile;

    @Value("${openclaw.cli.state-dir:}")
    private String cliStateDir;

    @Override
    public String analyzePreviousProgress(String meetingId,
                                          String previousMeetingId,
                                          String previousTitle,
                                          Map<String, Integer> todoStats,
                                          String delayedItems,
                                          String feishuMultitableDirective) {
        if (!enabled) {
            log.info("OpenClaw Agent disabled, skip progress analysis");
            return null;
        }

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

    @Override
    public String runMatterProgressReport(Meeting meeting, String bitableDirective) {
        if (!enabled) {
            log.info("OpenClaw Agent disabled, skip matter progress");
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

    @Override
    public String enhanceMeetingMinutes(String meetingId,
                                        String rawMinute,
                                        String meetingTitle,
                                        Integer meetingType,
                                        String participants,
                                        String transcriptText) {
        if (!enabled) {
            log.info("OpenClaw Agent disabled, skip minute enhancement");
            return rawMinute;
        }

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

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    /**
     * 通过 {@code openclaw agent} 子进程同步调用 Agent。
     */
    private String callAgent(String prompt, String taskType) {
        log.info("OpenClaw Agent call: taskType={}, promptLength={}", taskType, prompt.length());

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "openclaw", "agent",
                    "--agent", agentName,
                    "--message", prompt,
                    "--timeout", String.valueOf(timeoutSeconds),
                    "--json"
            );
            pb.redirectErrorStream(true);
            pb.directory(new java.io.File(System.getProperty("user.home")));

            Map<String, String> env = pb.environment();
            if (cliProfile != null && !cliProfile.isBlank()) {
                env.put("OPENCLAW_PROFILE", cliProfile);
            }
            if (cliStateDir != null && !cliStateDir.isBlank()) {
                env.put("OPENCLAW_STATE_DIR", cliStateDir);
            }

            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds + 10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("OpenClaw Agent timeout: taskType={}", taskType);
                return null;
            }

            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                log.warn("OpenClaw Agent failed: exitCode={}, output={}", process.exitValue(), output);
                return null;
            }

            return parseCliJsonResponse(output, taskType);

        } catch (Exception e) {
            log.error("OpenClaw Agent exception: taskType={}, error={}", taskType, e.getMessage());
            return null;
        }
    }

    /**
     * 从 CLI {@code --json} 标准输出中提取 payloads[0].text。
     */
    private String parseCliJsonResponse(String jsonOutput, String taskType) {
        try {
            JsonNode json = objectMapper.readTree(jsonOutput);

            if (json.path("status").asText().equals("ok")) {
                JsonNode payloads = json.path("result").path("payloads");
                if (payloads.isArray() && payloads.size() > 0) {
                    String text = payloads.get(0).path("text").asText();
                    log.info("OpenClaw Agent success: taskType={}, replyLength={}", taskType, text.length());
                    return text;
                }
            }

            log.warn("OpenClaw Agent non-ok status: {}", json.path("status").asText());
            return null;

        } catch (Exception e) {
            log.error("OpenClaw CLI response parse error: {}", e.getMessage());
            return null;
        }
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
