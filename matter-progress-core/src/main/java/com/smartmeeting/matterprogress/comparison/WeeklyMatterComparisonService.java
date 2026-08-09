package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.config.MatterProgressConfigRepository;
import com.smartmeeting.matterprogress.feishu.FeishuDocClient;
import com.smartmeeting.matterprogress.job.JdbcWeeklyComparisonJobRepository;
import com.smartmeeting.matterprogress.minute.MeetingMinuteQuery;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.MinuteSnapshot;
import com.smartmeeting.matterprogress.model.ParsedComparisonItems;
import com.smartmeeting.matterprogress.model.SourceDataSnapshot;
import com.smartmeeting.matterprogress.model.WeeklyComparisonItem;
import com.smartmeeting.matterprogress.model.WeeklyComparisonJob;
import com.smartmeeting.matterprogress.model.WeeklyComparisonResult;
import com.smartmeeting.matterprogress.model.WeeklyComparisonRun;
import com.smartmeeting.matterprogress.oabp.OabpSourceQuery;
import com.smartmeeting.matterprogress.report.JdbcWeeklyComparisonItemRepository;
import com.smartmeeting.matterprogress.report.JdbcWeeklyComparisonRunRepository;
import com.smartmeeting.matterprogress.report.WeeklyComparisonItemsJsonParser;
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
 *   <li><b>MCP 全托管</b>：WS 下发 oabp SQL → Agent 查库 → 返回 items JSON → Bot 入库</li>
 *   <li><b>Legacy</b>：Java 查 oabp + LLM 产 Markdown → 解析为 items → Bot 入库</li>
 * </ul>
 * <p>v0.26：产物入库（run + item 两表），不再写飞书 Doc。
 */
public class WeeklyMatterComparisonService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyMatterComparisonService.class);

    private final JdbcWeeklyComparisonJobRepository jobRepository;
    private final MatterProgressConfigRepository configRepository;
    private final MeetingMinuteQuery minuteQuery;
    private final FeishuDocClient feishuDocClient;
    private final ComparisonReportGenerator reportGenerator;
    private final OpenClawMcpWeeklyComparisonDelegate mcpDelegate;
    private final OabpSourceQuery oabpSourceQuery;
    private final JdbcWeeklyComparisonRunRepository runRepository;
    private final JdbcWeeklyComparisonItemRepository itemRepository;
    private final WeeklyComparisonItemsJsonParser itemsJsonParser;
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
            OabpSourceQuery oabpSourceQuery,
            JdbcWeeklyComparisonRunRepository runRepository,
            JdbcWeeklyComparisonItemRepository itemRepository,
            WeeklyComparisonItemsJsonParser itemsJsonParser,
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
        this.oabpSourceQuery = oabpSourceQuery;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.itemsJsonParser = itemsJsonParser;
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

    private WeeklyComparisonResult runJobViaOpenClawMcp(WeeklyComparisonJob job, Instant started) {
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
        List<MatterProgressConfigRow> sourceRows = loadSourceRowsOptional(job);
        log.info("Weekly comparison MCP dispatch jobId={} sourceRows={} (fixed oabp three-table SQL via Agent)",
                job.id(), sourceRows.size());

        OpenClawMcpWeeklyComparisonDelegate.McpWeeklyComparisonResult mcpResult = mcpDelegate.execute(
                job, sourceRows, Optional.of(outputRow), readOutputFeishuDocUrl, itemsJsonParser);

        if (!mcpResult.success()) {
            if (legacyFallbackOnMcpFailure) {
                log.warn("OpenClaw MCP 失败，降级 Legacy: jobId={} err={}", job.id(), mcpResult.errorMessage());
                return runJobLegacyPipeline(job, started);
            }
            throw new IllegalStateException("OpenClaw MCP 失败: " + mcpResult.errorMessage());
        }

        ParsedComparisonItems parsed = mcpResult.parsedItems();
        return persistRunAndItems(job, outputRow, parsed, started);
    }

    private WeeklyComparisonResult runJobLegacyPipeline(WeeklyComparisonJob job, Instant started) {
        List<SourceDataSnapshot> sources = collectSourceData(job);
        List<MinuteSnapshot> minutes;
        try {
            minutes = resolveMinutes(job);
        } catch (Exception e) {
            throw new IllegalStateException("纪要查询失败: " + e.getMessage(), e);
        }
        log.info("Weekly comparison collected jobId={} sources={} minutes={}", job.id(), sources.size(), minutes.size());
        ParsedComparisonItems parsed = reportGenerator.generate(job.id(), sources, minutes);
        MatterProgressConfigRow outputRow = configRepository.findByConfigName(job.outputConfigName())
                .orElseThrow(() -> new IllegalStateException("OUTPUT 配置不存在: " + job.outputConfigName()));
        return persistRunAndItems(job, outputRow, parsed, started);
    }

    /**
     * 入库分流：Agent 自行写库（parsed.runId>0）→ 校验+回写；否则 Bot 入库 run+item。
     */
    private WeeklyComparisonResult persistRunAndItems(WeeklyComparisonJob job,
                                                      MatterProgressConfigRow outputRow,
                                                      ParsedComparisonItems parsed,
                                                      Instant started) {
        if (parsed.agentWroteRun()) {
            return persistAgentWrittenRun(job, outputRow, parsed, started);
        }
        return persistBotWrittenRun(job, outputRow, parsed, started);
    }

    /**
     * Agent 已自行 INSERT run+item（§5）；Bot 跳过入库，仅做 count 校验、回填 run 状态、回写 host_agenda/job。
     */
    private WeeklyComparisonResult persistAgentWrittenRun(WeeklyComparisonJob job,
                                                          MatterProgressConfigRow outputRow,
                                                          ParsedComparisonItems parsed,
                                                          Instant started) {
        long runId = parsed.runId();
        int declaredCount = parsed.items().size();
        int actualCount = runRepository.countItemsByRunId(runId);
        Instant generatedAt = Instant.now();

        String runStatus;
        String runError;
        if (!parsed.success()) {
            runStatus = WeeklyComparisonRun.FAILED;
            runError = parsed.errorMessage();
            runRepository.markFailed(runId, runError);
            jobRepository.updateRunResult(job.id(), "FAILED", runError, started, runId);
            log.warn("Weekly comparison agent-wrote run parse failed jobId={} runId={} err={}",
                    job.id(), runId, runError);
            return WeeklyComparisonResult.failed(job.id(), runError);
        } else if (actualCount != declaredCount) {
            runStatus = WeeklyComparisonRun.PARTIAL;
            runError = "Agent 写入 item 数与回传不一致: declared=" + declaredCount + " actual=" + actualCount;
            runRepository.updateStatus(runId, actualCount, runStatus, runError);
            log.warn("Weekly comparison agent-wrote run count mismatch jobId={} runId={} {}",
                    job.id(), runId, runError);
        } else {
            runStatus = parsed.resolveRunStatus();
            runError = buildRunError(parsed);
            runRepository.updateStatus(runId, actualCount, runStatus, runError);
            log.info("Weekly comparison agent-wrote run ok jobId={} runId={} status={} items={}",
                    job.id(), runId, runStatus, actualCount);
        }

        configRepository.writeGeneratedReportRun(job.outputConfigName(), runId, generatedAt);
        jobRepository.updateRunResult(job.id(), "SUCCESS", runError, started, runId);

        if (WeeklyComparisonRun.PARTIAL.equals(runStatus)) {
            return WeeklyComparisonResult.partial(job.id(), runId, actualCount);
        }
        return WeeklyComparisonResult.success(job.id(), runId, actualCount);
    }

    /**
     * Bot 入库 run + items（旧模式 / Legacy / Agent 未写库降级）；写回 host_agenda.runId 与 job.last_run_id。
     */
    private WeeklyComparisonResult persistBotWrittenRun(WeeklyComparisonJob job,
                                                         MatterProgressConfigRow outputRow,
                                                         ParsedComparisonItems parsed,
                                                         Instant started) {
        String title = formatTitle(job.outputDocTitleTpl());
        Instant generatedAt = Instant.now();
        String runStatus = parsed.resolveRunStatus();
        String runError = buildRunError(parsed);

        WeeklyComparisonRun run = new WeeklyComparisonRun(
                null,
                job.id(),
                job.outputConfigName(),
                outputRow.presetTypeCode(),
                outputRow.agendaIndex(),
                title,
                parsed.items().size(),
                runStatus,
                generatedAt,
                runError);
        long runId = runRepository.insertRun(run);

        if (parsed.success() && !parsed.items().isEmpty()) {
            itemRepository.batchInsert(runId, parsed.items());
            runRepository.updateStatus(runId, parsed.items().size(), runStatus, runError);
        } else if (parsed.success()) {
            // 0 条事项但解析成功 → READY(item_count=0)
            runRepository.updateStatus(runId, 0, WeeklyComparisonRun.READY, null);
        } else {
            runRepository.markFailed(runId, runError);
            jobRepository.updateRunResult(job.id(), "FAILED", runError, started, runId);
            log.warn("Weekly comparison parse failed jobId={} runId={} err={}", job.id(), runId, runError);
            return WeeklyComparisonResult.failed(job.id(), runError);
        }

        configRepository.writeGeneratedReportRun(job.outputConfigName(), runId, generatedAt);
        jobRepository.updateRunResult(job.id(), "SUCCESS", null, started, runId);
        log.info("Weekly comparison success jobId={} runId={} status={} items={}",
                job.id(), runId, runStatus, parsed.items().size());

        if (WeeklyComparisonRun.PARTIAL.equals(runStatus)) {
            return WeeklyComparisonResult.partial(job.id(), runId, parsed.items().size());
        }
        return WeeklyComparisonResult.success(job.id(), runId, parsed.items().size());
    }

    private static String buildRunError(ParsedComparisonItems parsed) {
        if (parsed.success() && parsed.discardedCount() == 0) {
            return null;
        }
        if (!parsed.success()) {
            return parsed.errorMessage();
        }
        return "部分事项解析被丢弃: " + parsed.discardedCount() + " 条（字段缺失或 statusLabel/category 不一致）";
    }

    /**
     * 收集 oabp SOURCE 数据：每条 source_config 对应父会序 oabpTaskSql，Legacy 路径 Java 预查。
     */
    public List<SourceDataSnapshot> collectSourceData(WeeklyComparisonJob job) {
        List<MatterProgressConfigRow> rows = loadAndValidateSourceRows(job);
        if (oabpSourceQuery == null) {
            throw new IllegalStateException(
                    "Legacy 路径需要 oabp 数据源：请设置 feishu.weekly-comparison.oabp.enabled=true");
        }
        List<SourceDataSnapshot> out = new ArrayList<>();
        for (MatterProgressConfigRow row : rows) {
            String markdown = oabpSourceQuery.queryAsMarkdown(row.oabpTaskSql());
            out.add(new SourceDataSnapshot(
                    row.configName(),
                    row.oabpTaskSql(),
                    row.resolvedOabpSchemaHint(),
                    markdown));
        }
        return out;
    }

    private List<MatterProgressConfigRow> loadSourceRowsOptional(WeeklyComparisonJob job) {
        List<String> names = job.sourceConfigNames();
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return configRepository.loadSources(names);
    }

    private List<MatterProgressConfigRow> loadAndValidateSourceRows(WeeklyComparisonJob job) {
        List<String> names = job.sourceConfigNames();
        if (names == null || names.isEmpty()) {
            throw new IllegalStateException("job.source_config_names 为空");
        }
        List<MatterProgressConfigRow> rows = configRepository.loadSources(names);
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            boolean found = rows.stream().anyMatch(r -> name.equals(r.configName()));
            if (!found) {
                Optional<MatterProgressConfigRow> row = configRepository.findByConfigName(name);
                if (row.isEmpty()) {
                    missing.add(name + "（配置不存在）");
                } else if (!row.get().hasOabpTaskSql()) {
                    missing.add(name + "（父会序未配置 oabpTaskSql）");
                } else {
                    missing.add(name + "（非 SOURCE/BOTH 或未启用）");
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("SOURCE oabp 配置无效: " + String.join("; ", missing));
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("无有效 SOURCE 行（需 oabpTaskSql + SOURCE/BOTH + enabled）");
        }
        return rows;
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
