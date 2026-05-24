package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.SourceDocSnapshot;
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

/** 使用直连 LLM 生成对比 Markdown（可选降级路径，主路径为 OpenClaw）。 */
public class LlmComparisonReportGenerator implements ComparisonReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmComparisonReportGenerator.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public LlmComparisonReportGenerator(RestTemplate restTemplate, String apiUrl, String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl != null ? apiUrl : "";
        this.apiKey = apiKey != null ? apiKey : "";
        this.model = model != null && !model.isBlank() ? model : "deepseek-chat";
    }

    /**
     * 兼容旧调用方（无 jobId）。
     */
    public String generate(List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        return generateWithLlm(sources, minutes);
    }

    @Override
    public String generate(long jobId, List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        return generateWithLlm(sources, minutes);
    }

    private String generateWithLlm(List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        StringBuilder user = new StringBuilder();
        user.append("请输出 Markdown 格式的事项对比通报，含：概览、事项表快照、纪要摘录、差异清单、延期风险、附录链接。\n\n");
        user.append("## 飞书资料\n");
        for (SourceDocSnapshot s : sources) {
            user.append("### ").append(s.configName()).append("\n");
            user.append("URL: ").append(s.feishuDocUrl()).append("\n");
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
                    Map.of("role", "system", "content", "你是会议事项对比分析助手，输出简洁 Markdown。"),
                    Map.of("role", "user", "content", user.toString())
            ));
            body.put("temperature", 0.35);
            body.put("max_tokens", 4096);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    resolveChatUrl(), HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode json = response.getBody();
            if (json != null && json.has("choices")) {
                String content = json.get("choices").get(0).path("message").path("content").asText("").trim();
                if (!content.isBlank()) {
                    return content;
                }
            }
        } catch (Exception e) {
            log.warn("LLM comparison failed: {}", e.getMessage());
        }
        return fallbackMarkdown(sources, minutes);
    }

    private static String fallbackMarkdown(List<SourceDocSnapshot> sources, List<MinuteSnapshot> minutes) {
        StringBuilder sb = new StringBuilder("# 事项对比通报（自动生成）\n\n");
        sb.append("## 概览\n\n共 ").append(sources.size()).append(" 份资料，")
                .append(minutes.size()).append(" 份纪要。\n\n");
        for (SourceDocSnapshot s : sources) {
            sb.append("- 资料 **").append(s.configName()).append("**: ")
                    .append(s.feishuDocUrl()).append("\n");
        }
        for (MinuteSnapshot m : minutes) {
            sb.append("- 纪要 **").append(m.title()).append("** (").append(m.meetingId()).append(")\n");
        }
        return sb.toString();
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
