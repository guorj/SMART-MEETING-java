package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.config.MatterProgressConfigRepository;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.feishu.FeishuDocClient;
import com.smartmeeting.matterprogress.job.JdbcWeeklyComparisonJobRepository;
import com.smartmeeting.matterprogress.minute.MeetingMinuteQuery;
import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.SourceDocSnapshot;
import com.smartmeeting.matterprogress.model.WeeklyComparisonJob;
import com.smartmeeting.matterprogress.model.WeeklyComparisonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会前对比 Facade。
 * <ul>
 *   <li><b>MCP 全托管</b>（{@code delegateToMcp=true}）：仅 WS 下发任务 → OpenClaw + MCP 读飞书/库、写 Doc、入库</li>
 *   <li><b>Legacy</b>：Java 预读飞书/纪要 → LLM 或 OpenClaw（带正文）→ Java 写 Doc</li>
 * </ul>
 */
public class WeeklyMatterComparisonService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyMatterComparisonService.class);

    private final JdbcWeeklyComparisonJobRepository jobRepository;
    private final MatterProgressConfigRepository configRepository;
    private final MeetingMinuteQuery minuteQuery;
    private final FeishuDocClient feishuDocClient;
    private final ComparisonReportGenerator reportGenerator;
    private final OpenClawMcpWeeklyComparisonDelegate mcpDelegate;
    private final ObjectMapper objectMapper;
    private final boolean readOutputFeishuDocUrl;
    private final boolean delegateToMcp;
    private final boolean legacyFallbackOnMcpFailure;

    public WeeklyMatterComparisonService(
            JdbcWeeklyComparisonJobRepository jobRepository,
            MatterProgressConfigRepository configRepository,
            MeetingMinuteQuery minuteQuery,
            FeishuDocClient feishuDocClient,
            ComparisonReportGenerator reportGenerator,
            OpenClawMcpWeeklyComparisonDelegate mcpDelegate,
            ObjectMapper objectMapper,
            boolean readOutputFeishuDocUrl,
            boolean delegateToMcp,
            boolean legacyFallbackOnMcpFailure) {
        this.jobRepository = jobRepository;
        this.configRepository = configRepository;
        this.minuteQuery = minuteQuery;
        this.feishuDocClient = feishuDocClient;
        this.reportGenerator = reportGenerator;
        this.mcpDelegate = mcpDelegate;
        this.objectMapper = objectMapper;
        this.readOutputFeishuDocUrl = readOutputFeishuDocUrl;
        this.delegateToMcp = delegateToMcp;
        this.legacyFallbackOnMcpFailure = legacyFallbackOnMcpFailure;
    }

    /** 执行一次完整对比流水线 */
    public WeeklyComparisonResult runJob(long jobId) {
        Instant started = Instant.now();
        WeeklyComparisonJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("job 不存在: " + jobId));
        if (!job.enabled()) {
            return WeeklyComparisonResult.failed(jobId, "job 未启用");
        }
        try {
            log.info("Weekly comparison start jobId={} outputConfig={} delegateToMcp={}",
                    jobId, job.outputConfigName(), delegateToMcp);
            if (delegateToMcp) {
                return runJobViaOpenClawMcp(job, started);
            }
            return runJobLegacyPipeline(job, started);
        } catch (Exception e) {
            log.warn("Weekly comparison failed jobId={}: {}", jobId, e.getMessage());
            jobRepository.updateRunResult(jobId, "FAILED", e.getMessage(), started);
            return WeeklyComparisonResult.failed(jobId, e.getMessage());
        }
    }

    private WeeklyComparisonResult runJobViaOpenClawMcp(WeeklyComparisonJob job, Instant started) throws Exception {
        if (mcpDelegate == null || !mcpDelegate.isAvailable()) {
            if (legacyFallbackOnMcpFailure) {
                log.warn("OpenClaw MCP 不可用，降级 Legacy 流水线: jobId={}", job.id());
                return runJobLegacyPipeline(job, started);
            }
            throw new IllegalStateException(
                    "已启用 feishu.weekly-comparison.openclaw.delegate-to-mcp，但 Gateway 未配置");
        }

        MatterProgressConfigRow outputRow = configRepository.findByConfigName(job.outputConfigName())
                .orElseThrow(() -> new IllegalStateException("OUTPUT 配置不存在: " + job.outputConfigName()));

        Instant reportAtBefore = outputRow.generatedReportAt();
        String reportUrlBefore = outputRow.generatedReportUrl();

        List<MatterProgressConfigRow> sourceRows = configRepository.loadSources(job.sourceConfigNames());
        log.info("Weekly comparison MCP dispatch jobId={} sourceRows={} (no Java pre-fetch)",
                job.id(), sourceRows.size());

        OpenClawMcpWeeklyComparisonDelegate.McpWeeklyComparisonResult mcpResult = mcpDelegate.execute(
                job, sourceRows, Optional.of(outputRow), readOutputFeishuDocUrl);

        if (!mcpResult.success()) {
            if (legacyFallbackOnMcpFailure) {
                log.warn("OpenClaw MCP 失败，降级 Legacy: jobId={} err={}", job.id(), mcpResult.errorMessage());
                return runJobLegacyPipeline(job, started);
            }
            throw new IllegalStateException("OpenClaw MCP 失败: " + mcpResult.errorMessage());
        }

        String reportUrl = resolveReportUrlAfterMcp(job, started, reportAtBefore, reportUrlBefore, mcpResult);
        if (reportUrl == null || reportUrl.isBlank()) {
            throw new IllegalStateException(
                    "OpenClaw MCP 已完成但未写回 generated_report_url，请检查 Skill/MCP 是否执行 UPDATE 与创建 Doc");
        }

        MatterProgressConfigRow after = configRepository.findByConfigName(job.outputConfigName()).orElse(outputRow);
        if (!reportUrl.equals(after.generatedReportUrl())) {
            configRepository.writeGeneratedReport(job.outputConfigName(), reportUrl, Instant.now());
            log.info("Weekly comparison MCP: Java 补写 generated_report_url jobId={}", job.id());
        }

        jobRepository.updateRunResult(job.id(), "SUCCESS", null, started);
        log.info("Weekly comparison MCP success jobId={} docUrl={}", job.id(), reportUrl);
        return WeeklyComparisonResult.success(job.id(), reportUrl);
    }

    private String resolveReportUrlAfterMcp(
            WeeklyComparisonJob job,
            Instant started,
            Instant reportAtBefore,
            String reportUrlBefore,
            OpenClawMcpWeeklyComparisonDelegate.McpWeeklyComparisonResult mcpResult) {
        if (mcpResult.extractedReportUrl() != null && !mcpResult.extractedReportUrl().isBlank()) {
            return mcpResult.extractedReportUrl();
        }
        MatterProgressConfigRow row = configRepository.findByConfigName(job.outputConfigName()).orElse(null);
        if (row == null) {
            return null;
        }
        String url = row.generatedReportUrl();
        Instant at = row.generatedReportAt();
        if (url != null && !url.isBlank()) {
            if (reportUrlBefore == null || !url.equals(reportUrlBefore)) {
                return url;
            }
            if (at != null && (reportAtBefore == null || at.isAfter(started.minusSeconds(2)))) {
                return url;
            }
        }
        return OpenClawMcpWeeklyComparisonDelegate.extractReportUrl(mcpResult.replyText());
    }

    private WeeklyComparisonResult runJobLegacyPipeline(WeeklyComparisonJob job, Instant started) throws Exception {
        List<SourceDocSnapshot> sources = collectSourceDocs(job);
        List<MinuteSnapshot> minutes = resolveMinutes(job);
        log.info("Weekly comparison collected jobId={} sources={} minutes={}", job.id(), sources.size(), minutes.size());
        String markdown = reportGenerator.generate(job.id(), sources, minutes);
        if (markdown == null || markdown.isBlank()) {
            throw new IllegalStateException(
                    "对比报告 Markdown 为空：请配置 feishu.weekly-comparison.openclaw.* 或 MEETING_LLM_API_KEY");
        }
        String title = formatTitle(job.outputDocTitleTpl());
        String docUrl = feishuDocClient.createAndWriteMarkdown(job.feishuFolderToken(), title, markdown);
        configRepository.writeGeneratedReport(job.outputConfigName(), docUrl, Instant.now());
        jobRepository.updateRunResult(job.id(), "SUCCESS", null, started);
        log.info("Weekly comparison legacy success jobId={} docUrl={}", job.id(), docUrl);
        return WeeklyComparisonResult.success(job.id(), docUrl);
    }

    /**
     * 收集待读飞书 URL：source_config_names + 可选 output 行 feishu_doc_url（Legacy 路径）。
     */
    public List<SourceDocSnapshot> collectSourceDocs(WeeklyComparisonJob job) {
        List<MatterProgressConfigRow> rows = configRepository.loadSources(job.sourceConfigNames());
        List<SourceDocSnapshot> out = new ArrayList<>();
        for (MatterProgressConfigRow row : rows) {
            String text = feishuDocClient.fetchPlainText(row.feishuDocUrl());
            out.add(new SourceDocSnapshot(row.configName(), row.feishuDocUrl(), text));
        }
        if (readOutputFeishuDocUrl) {
            configRepository.findByConfigName(job.outputConfigName()).ifPresent(output -> {
                if (output.feishuDocUrl() != null && !output.feishuDocUrl().isBlank()) {
                    boolean already = rows.stream().anyMatch(r -> r.configName().equals(output.configName()));
                    if (!already) {
                        String text = feishuDocClient.fetchPlainText(output.feishuDocUrl());
                        out.add(new SourceDocSnapshot(output.configName(), output.feishuDocUrl(), text));
                    }
                }
            });
        }
        return out;
    }

    private List<MinuteSnapshot> resolveMinutes(WeeklyComparisonJob job) throws Exception {
        JsonNode params = objectMapper.readTree(job.minuteQueryParamsJson());
        String type = job.minuteQueryType() != null ? job.minuteQueryType().trim().toUpperCase() : "";
        return switch (type) {
            case "PRESET_LAST_7_DAYS" -> {
                int preset = params.path("presetTypeCode").asInt(1);
                int days = params.path("days").asInt(7);
                yield minuteQuery.templateMinutesSinceDays(preset, days);
            }
            case "MEETING_IDS" -> {
                List<String> ids = new ArrayList<>();
                JsonNode arr = params.path("meetingIds");
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        ids.add(n.asText());
                    }
                }
                yield minuteQuery.byMeetingIds(ids);
            }
            default -> throw new IllegalArgumentException("未知 minute_query_type: " + job.minuteQueryType());
        };
    }

    private static String formatTitle(String tpl) {
        String t = tpl != null && !tpl.isBlank() ? tpl : "事项对比通报-{date}";
        return t.replace("{date}", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
    }
}
