package com.smartmeeting.matterprogress.comparison;

import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.SourceDocSnapshot;
import com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient;
import com.smartmeeting.matterprogress.openclaw.OpenClawReplyExtractor;
import com.smartmeeting.matterprogress.openclaw.OpenClawSessionKeys;
import com.smartmeeting.matterprogress.openclaw.OpenClawSkillPromptBuilder;
import com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 通过 OpenClaw Gateway + matter-progress Skill 生成对比通报 Markdown。
 *
 * <p>主路径：构建 {@code /skill:matter-progress} prompt → WS {@code chat.send} → 提取回复。
 * Gateway 不可用或返回空时返回 {@code null}，由 {@link WeeklyMatterComparisonService} 记 FAILED。
 */
public class OpenClawComparisonReportGenerator implements ComparisonReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenClawComparisonReportGenerator.class);

    private final OpenClawGatewayWsClient gatewayClient;
    private final String gatewayUrl;
    private final String authToken;
    private final String deviceToken;
    private final String sessionKey;
    private final int timeoutSeconds;
    private final boolean skillMode;

    public OpenClawComparisonReportGenerator(OpenClawGatewayWsClient gatewayClient,
                                             String gatewayUrl,
                                             String authToken,
                                             String deviceToken,
                                             String sessionKey,
                                             int timeoutSeconds,
                                             boolean skillMode) {
        this.gatewayClient = gatewayClient;
        this.gatewayUrl = gatewayUrl;
        this.authToken = authToken;
        this.deviceToken = deviceToken;
        this.sessionKey = sessionKey;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        this.skillMode = skillMode;
    }

    public boolean isAvailable() {
        boolean hasAuth = (authToken != null && !authToken.isBlank())
                || (deviceToken != null && !deviceToken.isBlank());
        return gatewayUrl != null && !gatewayUrl.isBlank() && hasAuth;
    }

    @Override
    public String generate(long jobId, List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        if (!isAvailable()) {
            log.warn("OpenClaw not available for comparison report: jobId={}", jobId);
            return null;
        }

        String taskId = OpenClawTaskIds.weeklyComparison(jobId);
        String effectiveSessionKey = OpenClawSessionKeys.resolveForTask(taskId, sessionKey);

        String prompt;
        if (skillMode) {
            prompt = buildSkillPrompt(jobId, sources, minutes, taskId);
        } else {
            prompt = buildFullPrompt(sources, minutes);
        }

        log.info("OpenClaw comparison call: jobId={}, taskId={}, skillMode={}, promptLength={}",
                jobId, taskId, skillMode, prompt.length());

        String body;
        try {
            body = gatewayClient.sendChatMessage(
                    gatewayUrl, authToken, deviceToken, effectiveSessionKey,
                    prompt, timeoutSeconds, taskId);
        } catch (Exception e) {
            log.warn("OpenClaw comparison WS call failed: jobId={}, error={}", jobId, e.getMessage());
            return null;
        }

        if (body == null || body.isBlank()) {
            log.warn("OpenClaw comparison empty response: jobId={}, taskId={}", jobId, taskId);
            return null;
        }

        String markdown = OpenClawReplyExtractor.extractFromBody(body);
        if (markdown == null || markdown.isBlank()) {
            log.warn("OpenClaw comparison could not extract text: jobId={}, taskId={}", jobId, taskId);
            return null;
        }

        log.info("OpenClaw comparison success: jobId={}, taskId={}, replyLength={}", jobId, taskId, markdown.length());
        return markdown;
    }

    private String buildSkillPrompt(long jobId, List<SourceDocSnapshot> sources,
                                    List<MinuteSnapshot> minutes, String taskId) {
        return OpenClawSkillPromptBuilder.build("matter-progress", map -> {
            map.put("taskId", taskId);
            map.put("jobId", String.valueOf(jobId));
            map.put("sourceCount", String.valueOf(sources.size()));
            map.put("minuteCount", String.valueOf(minutes.size()));
            // 附带来源名称供 Skill 决策是否使用 lark-mcp 补读
            StringBuilder sourceNames = new StringBuilder();
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) sourceNames.append(",");
                sourceNames.append(sources.get(i).configName());
            }
            map.put("sourceNames", sourceNames.toString());
        }) + "\n\n" + buildDataContext(sources, minutes);
    }

    private String buildFullPrompt(List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        return "请输出 Markdown 格式的事项对比通报，含：概览、事项表快照、纪要摘录、差异清单、延期风险、附录链接。\n\n"
                + buildDataContext(sources, minutes);
    }

    private String buildDataContext(List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 飞书资料\n");
        for (SourceDocSnapshot s : sources) {
            sb.append("### ").append(s.configName()).append("\n");
            sb.append("URL: ").append(s.feishuDocUrl()).append("\n");
            String text = s.plainText() != null ? s.plainText() : "";
            if (text.length() > 8000) {
                text = text.substring(0, 8000) + "…";
            }
            sb.append(text).append("\n\n");
        }
        sb.append("## 会议纪要\n");
        for (MinuteSnapshot m : minutes) {
            sb.append("### ").append(m.title() != null ? m.title() : m.meetingId()).append("\n");
            sb.append("meetingId=").append(m.meetingId()).append("\n");
            String md = m.contentMarkdown() != null ? m.contentMarkdown() : "";
            if (md.length() > 8000) {
                md = md.substring(0, 8000) + "…";
            }
            sb.append(md).append("\n\n");
        }
        return sb.toString();
    }
}
