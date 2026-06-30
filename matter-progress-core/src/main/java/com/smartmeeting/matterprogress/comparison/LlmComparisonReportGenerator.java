package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.SourceDataSnapshot;
import com.smartmeeting.matterprogress.report.WeeklyComparisonItemMarkdownParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy 路径：直连 LLM 生成 Markdown，再由 {@link WeeklyComparisonItemMarkdownParser} 解析为 items。
 * <p>解析失败 → {@link ParsedComparisonItems#failed} → run FAILED（不写 READY+0 条）。
 */
public class LlmComparisonReportGenerator implements ComparisonReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmComparisonReportGenerator.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final WeeklyComparisonItemMarkdownParser markdownParser = new WeeklyComparisonItemMarkdownParser();

    public LlmComparisonReportGenerator(RestTemplate restTemplate, String apiUrl, String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl != null ? apiUrl : "";
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "deepseek-chat";
    }

    @Override
    public ParsedComparisonItems generate(long jobId, List<SourceDataSnapshot> sources, List<MinuteSnapshot> minutes) {
        String markdown = generateMarkdown(sources, minutes);
        if (markdown == null || markdown.isBlank()) {
            return ParsedComparisonItems.failed("LLM 产出 Markdown 为空");
        }
        return markdownParser.parse(markdown);
    }

    private String generateMarkdown(List<SourceDataSnapshot> sources, List<MinuteSnapshot> minutes) {
        StringBuilder user = new StringBuilder();
        user.append("请输出 Markdown 格式的事项对比通报。\n");
        user.append("必须严格按以下顺序输出三组（组标题用 `##`）：\n");
        user.append("1. `## 延期事项`\n2. `## 已完成事项`\n3. `## 进行中事项`\n\n");
        user.append("每条事项一行，格式：`- 事项：<内容>，责任人：<姓名>，时间节点：<时间>，状态：<延期|已完成|进行中>`\n");
        user.append("某分组无数据输出 `- 无`。状态必须与所在分组一致。\n\n");
        user.append("## oabp 事项表\n");
        for (SourceDataSnapshot s : sources) {
            user.append("### ").append(s.configName()).append("\n");
            user.append("schema: ").append(s.oabpSchemaHint()).append("\n");
            String body = s.plainText() != null ? s.plainText() : "";
            if (body.length() > 8000) {
                body = body.substring(0, 8000) + "…";
            }
            user.append(body).append("\n\n");
        }
        user.append("## 会议纪要\n");
        for (MinuteSnapshot m : minutes) {
            user.append("### ").append(m.title() != null ? m.title() : m.meetingId()).append("\n");
            user.append("meetingId=").append(m.meetingId()).append("\n");
            String md = m.contentMarkdown() != null ? m.contentMarkdown() : "";
            if (md.length() > 8000) {
                md = md.substring(0, 8000) + "…";
            }
            user.append(md).append("\n\n");
        }
        if (apiKey.isBlank() || "test".equals(apiKey)) {
            return fallbackMarkdown(sources, minutes);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是会议事项对比分析助手，输出清晰 Markdown。"),
                    Map.of("role", "user", "content", user.toString())));
            body.put("temperature", 0.3);
            body.put("max_tokens", 4096);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    resolveChatUrl(), HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode root = resp.getBody();
            if (root != null) {
                JsonNode content = root.path("choices").path(0).path("message").path("content");
                if (content.isTextual() && !content.asText().isBlank()) {
                    return content.asText();
                }
            }
        } catch (Exception e) {
            log.warn("LLM comparison failed: {}", e.getMessage());
        }
        return fallbackMarkdown(sources, minutes);
    }

    private static String fallbackMarkdown(List<SourceDataSnapshot> sources, List<MinuteSnapshot> minutes) {
        // fallback 不产出有效三组结构，解析器会返回 failed → run FAILED
        return "# 事项对比通报（自动生成 - 无 LLM）\n\nLLM 未配置或调用失败，无法生成结构化事项。\n";
    }

    private String resolveChatUrl() {
        String u = apiUrl.trim();
        if (u.contains("chat/completions")) {
            return u;
        }
        String base = u.isEmpty() ? "https://api.deepseek.com" : u.replaceAll("/$", "");
        return base + "/v1/chat/completions";
    }
}
