package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.api.dto.MatterProgressReportResponse;
import com.smartmeeting.entity.MatterProgressDocConfig;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MatterProgressDocConfigMapper;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事项进度通报：综合管理会临时策略下由 OpenClaw CLI 读飞书多维表直接生成 Markdown；否则正文来自飞书 Docx 或 classpath，再经 meeting.llm。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatterProgressReportService {

    private final MatterProgressDocConfigMapper configMapper;
    private final MeetingMapper meetingMapper;
    private final RestTemplate restTemplate;
    private final ResourceLoader resourceLoader;
    private final FeishuService feishuService;
    private final AiAgentService aiAgentService;
    private final OpenclawComprehensiveBitableBranch comprehensiveBitableBranch;

    @Value("${meeting.llm.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String llmApiUrl;

    @Value("${meeting.llm.api-key:}")
    private String llmApiKey;

    @Value("${meeting.llm.model:deepseek-chat}")
    private String llmModel;

    /** 仅测试/联调：未配置飞书字段时是否加载 classpath 样例正文（生产须为 false） */
    @Value("${meeting.matter-progress.allow-classpath-fallback:false}")
    private boolean allowClasspathFallback;

    @Value("${meeting.matter-progress.classpath-sample:classpath:matter-progress/fallback-sample.txt}")
    private String classpathSampleLocation;

    public MatterProgressReportResponse analyzeForMeeting(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }

        String bitableDirective = comprehensiveBitableBranch.buildDirectiveForRecordingMatterProgress(meeting);
        if (bitableDirective != null) {
            String openclawMd = aiAgentService.runMatterProgressReportViaOpenclaw(meeting, bitableDirective);
            if (openclawMd != null && !openclawMd.isBlank()) {
                MatterProgressDocConfig cfgForName = loadActiveConfig();
                String configName = (cfgForName != null && cfgForName.getConfigName() != null)
                        ? cfgForName.getConfigName()
                        : "comprehensive-bitable-openclaw";
                return MatterProgressReportResponse.builder()
                        .analysisMarkdown(openclawMd)
                        .source("feishu_bitable_openclaw")
                        .configName(configName)
                        .build();
            }
            log.warn("OpenClaw matter_progress_bitable 无有效返回，回退 Docx/classpath 链路 meetingId={}", meetingId);
        }

        MatterProgressDocConfig cfg = loadActiveConfig();
        if (cfg == null) {
            throw new BusinessException(400,
                    "未配置事项进度文档：请在表 int_matter_progress_doc_config 中新增并启用一条记录；"
                            + "若仅使用综合管理会多维表临时分支，请确认 OPENCLAW_COMP_BITABLE_PROGRESS=true、预设码命中且本机 openclaw CLI 可用");
        }

        String bodyText;
        String source;

        String documentId = MatterProgressDocxIdResolver.resolveDocumentId(cfg);
        if (documentId != null && !documentId.isBlank()) {
            try {
                bodyText = feishuService.fetchDocxPlainText(documentId);
            } catch (Exception e) {
                log.warn("飞书 Docx 正文拉取失败 documentId={}: {}", documentId, e.getMessage());
                throw new BusinessException(502, "飞书文档正文拉取失败: " + e.getMessage());
            }
            if (bodyText == null || bodyText.isBlank()) {
                throw new BusinessException(400, "飞书文档已读取但提取的正文为空，请确认文档内有文本内容");
            }
            source = "feishu_docx";
        } else if (MatterProgressDocxIdResolver.hasFeishuFields(cfg)) {
            throw new BusinessException(400,
                    "已填写飞书文档链接但无法解析：请使用包含 /docx/、/wiki/ 或 /base/?table= 的完整 HTTPS 链接");
        } else if (allowClasspathFallback) {
            bodyText = readClasspathSample();
            if (bodyText == null || bodyText.isBlank()) {
                throw new BusinessException(400, "已开启 classpath 联调样例但未读到有效内容，请检查 meeting.matter-progress.classpath-sample");
            }
            source = "classpath_fallback";
        } else {
            throw new BusinessException(400,
                    "未配置可用的飞书资料：请在 int_matter_progress_doc_config 填写 feishu_doc_url（docx/wiki/base 完整链接）；"
                            + "本地测试可设置 meeting.matter-progress.allow-classpath-fallback=true");
        }

        String analysis = callLlmForProgressReport(meeting, bodyText);
        return MatterProgressReportResponse.builder()
                .analysisMarkdown(analysis)
                .source(source)
                .configName(cfg.getConfigName() != null ? cfg.getConfigName() : "default")
                .build();
    }

    private MatterProgressDocConfig loadActiveConfig() {
        LambdaQueryWrapper<MatterProgressDocConfig> q = new LambdaQueryWrapper<>();
        q.eq(MatterProgressDocConfig::getEnabled, 1).orderByAsc(MatterProgressDocConfig::getId).last("LIMIT 1");
        MatterProgressDocConfig c = configMapper.selectOne(q);
        if (c != null) {
            return c;
        }
        return configMapper.selectById(1L);
    }

    private String readClasspathSample() {
        try {
            Resource r = resourceLoader.getResource(classpathSampleLocation);
            if (!r.exists()) {
                return null;
            }
            return StreamUtils.copyToString(r.getInputStream(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("无法读取事项进度 classpath 样例: {}", e.getMessage());
            return null;
        }
    }

    private String callLlmForProgressReport(Meeting meeting, String documentBody) {
        String prompt = buildUserPrompt(meeting, documentBody);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (llmApiKey != null && !llmApiKey.isBlank()) {
            headers.setBearerAuth(llmApiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", llmModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "你是企业会务与项目管理助手。请根据用户提供的「云文档或待办进度正文」输出**事项进度通报**，使用 Markdown（含分级标题、表格或列表），"
                                + "突出：已完成项、进行中项、延期/风险项及建议关注点。语言简洁专业。"),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("temperature", 0.35);
        body.put("max_tokens", 3500);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = resolveChatCompletionsUrl();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode json = response.getBody();
            if (json != null && json.has("choices")) {
                return json.get("choices").get(0).path("message").path("content").asText();
            }
        } catch (Exception e) {
            log.warn("事项进度通报 LLM 调用失败: {}", e.getMessage());
        }
        return fallbackReport(meeting.getTitle(), documentBody);
    }

    private String resolveChatCompletionsUrl() {
        String u = llmApiUrl != null ? llmApiUrl.trim() : "";
        if (u.contains("chat/completions")) {
            return u;
        }
        String base = u.isEmpty() ? "https://api.deepseek.com" : u.replaceAll("/$", "");
        return base + "/v1/chat/completions";
    }

    private static String buildUserPrompt(Meeting meeting, String documentBody) {
        return "## 当前会议\n"
                + "- 主题：" + meeting.getTitle() + "\n"
                + "- 集团/会议组：" + meeting.getCompany() + " / " + meeting.getGroupName() + "\n\n"
                + "## 待分析的文档/进度正文\n"
                + documentBody + "\n\n"
                + "## 输出要求\n"
                + "请输出「事项进度通报」Markdown，建议包含：概览、分项进度表、风险与需协调事项、下一步建议。";
    }

    private static String fallbackReport(String meetingTitle, String documentBody) {
        String excerpt = documentBody.length() > 800 ? documentBody.substring(0, 800) + "…" : documentBody;
        return "## 事项进度通报（离线摘要）\n\n"
                + "当前会议：**" + meetingTitle + "**\n\n"
                + "大模型暂不可用，以下为配置正文摘录：\n\n"
                + "```\n" + excerpt + "\n```\n\n"
                + "请检查 `meeting.llm.api-key` / 网络后重试「事项进度通报」。";
    }
}
