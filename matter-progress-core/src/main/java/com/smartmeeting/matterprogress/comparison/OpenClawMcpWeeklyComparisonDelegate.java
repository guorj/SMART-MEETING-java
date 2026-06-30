package com.smartmeeting.matterprogress.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.matterprogress.model.MatterProgressConfigRow;
import com.smartmeeting.matterprogress.model.WeeklyComparisonJob;
import com.smartmeeting.matterprogress.openclaw.OpenClawGatewayWsClient;
import com.smartmeeting.matterprogress.openclaw.OpenClawReplyExtractor;
import com.smartmeeting.matterprogress.openclaw.OpenClawSessionKeys;
import com.smartmeeting.matterprogress.openclaw.OpenClawSkillPromptBuilder;
import com.smartmeeting.matterprogress.openclaw.OpenClawTaskIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weekly comparison OpenClaw full-delegate: WS dispatch; Agent uses meeting-mysql for oabp SOURCE + minutes.
 * Bot parses {@code generatedReportUrl=} and JDBC writeback.
 */
public class OpenClawMcpWeeklyComparisonDelegate {

    private static final Logger log = LoggerFactory.getLogger(OpenClawMcpWeeklyComparisonDelegate.class);

    private static final Pattern FEISHU_REPORT_URL = Pattern.compile(
            "https://[\\w.-]+\\.feishu\\.cn/(?:docx|wiki|base)/[^\\s\"'<>]+",
            Pattern.CASE_INSENSITIVE);

    private static final String INSTRUCTIONS_TEMPLATE = loadClasspathUtf8("/weekly-comparison-mcp-instructions.template");

    private final OpenClawGatewayWsClient gatewayClient;
    private final ObjectMapper objectMapper;
    private final String gatewayUrl;
    private final String authToken;
    private final String deviceToken;
    private final String sessionKey;
    private final int timeoutSeconds;
    private final boolean skillMode;
    private final String weeklyComparisonFeishuAppId;
    private final String defaultOabpSchema;

    public OpenClawMcpWeeklyComparisonDelegate(
            OpenClawGatewayWsClient gatewayClient,
            ObjectMapper objectMapper,
            String gatewayUrl,
            String authToken,
            String deviceToken,
            String sessionKey,
            int timeoutSeconds,
            boolean skillMode,
            String weeklyComparisonFeishuAppId,
            String defaultOabpSchema) {
        this.gatewayClient = gatewayClient;
        this.objectMapper = objectMapper;
        this.gatewayUrl = gatewayUrl;
        this.authToken = authToken;
        this.deviceToken = deviceToken;
        this.sessionKey = sessionKey;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 120;
        this.skillMode = skillMode;
        this.weeklyComparisonFeishuAppId = weeklyComparisonFeishuAppId != null ? weeklyComparisonFeishuAppId : "";
        this.defaultOabpSchema = defaultOabpSchema != null && !defaultOabpSchema.isBlank()
                ? defaultOabpSchema.trim()
                : MatterProgressConfigRow.DEFAULT_OABP_SCHEMA;
    }

    public OpenClawMcpWeeklyComparisonDelegate(
            OpenClawGatewayWsClient gatewayClient,
            ObjectMapper objectMapper,
            String gatewayUrl,
            String authToken,
            String deviceToken,
            String sessionKey,
            int timeoutSeconds,
            boolean skillMode,
            String weeklyComparisonFeishuAppId) {
        this(gatewayClient, objectMapper, gatewayUrl, authToken, deviceToken, sessionKey,
                timeoutSeconds, skillMode, weeklyComparisonFeishuAppId, MatterProgressConfigRow.DEFAULT_OABP_SCHEMA);
    }

    public boolean isAvailable() {
        boolean hasAuth = (authToken != null && !authToken.isBlank())
                || (deviceToken != null && !deviceToken.isBlank());
        return gatewayUrl != null && !gatewayUrl.isBlank() && hasAuth;
    }

    public McpWeeklyComparisonResult execute(
            WeeklyComparisonJob job,
            List<MatterProgressConfigRow> sourceRows,
            Optional<MatterProgressConfigRow> outputRow,
            boolean readOutputFeishuDocUrl,
            com.smartmeeting.matterprogress.report.WeeklyComparisonItemsJsonParser itemsParser) {
        if (!isAvailable()) {
            return McpWeeklyComparisonResult.failed("OpenClaw Gateway not configured (gateway-url / auth-token)");
        }
        if (!skillMode) {
            return McpWeeklyComparisonResult.failed("weekly-comparison MCP requires openclaw.skill-mode=true");
        }
        String validationError = validateSourceRows(sourceRows, job);
        if (validationError != null) {
            return McpWeeklyComparisonResult.failed(validationError);
        }

        String taskId = OpenClawTaskIds.weeklyComparison(job.id());
        long runNonceMs = System.currentTimeMillis();
        String runKey = OpenClawTaskIds.weeklyComparisonRun(job.id(), runNonceMs);
        String sessionKeyForTask = OpenClawSessionKeys.resolveForTask(runKey, sessionKey);
        String prompt = buildMcpSkillPrompt(job, sourceRows, outputRow, readOutputFeishuDocUrl, taskId);

        log.info("OpenClaw MCP weekly-comparison WS: jobId={} taskId={} runKey={} sessionKey={} gatewayUrl={} sources={}",
                job.id(), taskId, runKey, sessionKeyForTask, gatewayUrl, sourceRows.size());
        log.info("OpenClaw MCP weekly-comparison prompt (full):\n---\n{}\n---", prompt);

        try {
            String body = gatewayClient.sendChatMessage(
                    gatewayUrl, authToken, deviceToken, sessionKeyForTask,
                    prompt, timeoutSeconds, runKey, "END_WEEKLY_COMPARISON_ITEMS-----");
            if (body == null || body.isBlank()) {
                return McpWeeklyComparisonResult.failed("OpenClaw empty reply");
            }
            String reply = OpenClawReplyExtractor.extractFromBody(body);
            String text = reply != null ? reply : body;
            com.smartmeeting.matterprogress.model.ParsedComparisonItems parsed = itemsParser.parse(text);
            log.info("OpenClaw MCP weekly-comparison done: jobId={} replyLength={} items={} discarded={} success={}",
                    job.id(), text.length(), parsed.items().size(), parsed.discardedCount(), parsed.success());
            if (!parsed.success()) {
                String preview = text.length() > 200 ? text.substring(0, 200) + "…" : text;
                return McpWeeklyComparisonResult.failed(
                        "Agent items 解析失败: " + parsed.errorMessage() + "; replyPreview=" + preview);
            }
            return McpWeeklyComparisonResult.ok(text, parsed);
        } catch (Exception e) {
            log.warn("OpenClaw MCP weekly-comparison WS failed: jobId={} {}", job.id(), e.getMessage());
            return McpWeeklyComparisonResult.failed(e.getMessage());
        }
    }

    static String validateSourceRows(List<MatterProgressConfigRow> sourceRows, WeeklyComparisonJob job) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return "无有效 SOURCE 行（需 oabpTaskSql + SOURCE/BOTH + enabled）";
        }
        List<String> names = job.sourceConfigNames();
        if (names != null && !names.isEmpty()) {
            for (String name : names) {
                boolean ok = sourceRows.stream().anyMatch(r -> name.equals(r.configName()) && r.hasOabpTaskSql());
                if (!ok) {
                    return "SOURCE 未配置 oabpTaskSql 或无效: " + name;
                }
            }
        }
        for (MatterProgressConfigRow row : sourceRows) {
            if (!row.hasOabpTaskSql()) {
                return "SOURCE 未配置 oabpTaskSql: " + row.configName();
            }
        }
        return null;
    }

    private String buildMcpSkillPrompt(
            WeeklyComparisonJob job,
            List<MatterProgressConfigRow> sourceRows,
            Optional<MatterProgressConfigRow> outputRow,
            boolean readOutputFeishuDocUrl,
            String taskId) {
        String header = OpenClawSkillPromptBuilder.build("matter-progress", map -> {
            map.put("mode", "weekly-comparison-mcp");
            map.put("skill", "matter-progress");
            map.put("taskId", taskId);
            map.put("jobId", String.valueOf(job.id()));
            map.put("jobName", nullToEmpty(job.jobName()));
            map.put("outputConfigName", nullToEmpty(job.outputConfigName()));
            map.put("outputDocTitle", formatTitle(job.outputDocTitleTpl()));
            map.put("feishuFolderToken", nullToEmpty(job.feishuFolderToken()));
            map.put("minuteQueryType", nullToEmpty(job.minuteQueryType()));
            map.put("readOutputFeishuDocUrl", String.valueOf(readOutputFeishuDocUrl));
            map.put("weeklyComparisonFeishuAppId", weeklyComparisonFeishuAppId);
            map.put("mcpMysql", "meeting-mysql__mysql_query");
            map.put("oabpSchema", defaultOabpSchema);
            map.put("minuteTable", "int_meeting_minute");
            map.put("minuteBodyColumn", "content_markdown");
            map.put("minuteFallbackColumn", "content_url");
            map.put("minuteStatusRequired", "READY");
            map.put("outputProtocol", "BEGIN/END_WEEKLY_COMPARISON_ITEMS JSON block (no Feishu Doc write)");
        });
        String dataBlock = buildStructuredDataBlock(job, sourceRows, outputRow, readOutputFeishuDocUrl);
        return header + "\n\n" + dataBlock + "\n\n" + buildMcpTaskInstructions(job, readOutputFeishuDocUrl);
    }

    private String buildStructuredDataBlock(
            WeeklyComparisonJob job,
            List<MatterProgressConfigRow> sourceRows,
            Optional<MatterProgressConfigRow> outputRow,
            boolean readOutputFeishuDocUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN_WEEKLY_COMPARISON_DATA-----\n");

        List<String> configNames = resolveSourceConfigNames(job, sourceRows);
        sb.append("[source_config_names]\n");
        for (String name : configNames) {
            sb.append(name).append('\n');
        }

        sb.append("[source_oabp_sql]\n");
        for (MatterProgressConfigRow row : sourceRows) {
            sb.append("config=").append(row.configName()).append('\n');
            sb.append("schema=").append(row.resolvedOabpSchemaHint()).append('\n');
            sb.append("sql=").append(row.oabpTaskSql().strip()).append('\n');
            sb.append("---\n");
        }

        if (readOutputFeishuDocUrl) {
            outputRow.ifPresent(out -> {
                if (out.feishuDocUrl() != null && !out.feishuDocUrl().isBlank()) {
                    sb.append("[output_reference_url]\n");
                    sb.append("config=").append(out.configName()).append('\n');
                    sb.append("url=").append(out.feishuDocUrl().trim()).append('\n');
                }
            });
        }

        sb.append("[minute_query_params]\n");
        sb.append("type=").append(nullToEmpty(job.minuteQueryType())).append('\n');
        sb.append("params=").append(compactJson(job.minuteQueryParamsJson())).append('\n');

        sb.append("[minute_query_sql]\n");
        sb.append(buildMinuteQuerySql(job)).append('\n');

        sb.append("[mysql_target]\n");
        sb.append("hint=meeting-mysql MCP: minutes use database=intelligence; SOURCE SQL runs against oabp schema\n");
        sb.append("database=intelligence\n");
        sb.append("oabp_schema=").append(defaultOabpSchema).append('\n');
        sb.append("tables=int_meeting_minute,int_matter_progress_doc_config\n");
        sb.append("oabp_note=Execute each source sql as-is (tables may be unqualified if MCP default DB is oabp); "
                + "or prefix tables with oabp_schema e.g. ").append(defaultOabpSchema).append(".jq_project_task_tracking\n");

        sb.append("-----END_WEEKLY_COMPARISON_DATA-----");
        return sb.toString();
    }

    private List<String> resolveSourceConfigNames(WeeklyComparisonJob job, List<MatterProgressConfigRow> sourceRows) {
        if (job.sourceConfigNames() != null && !job.sourceConfigNames().isEmpty()) {
            return job.sourceConfigNames();
        }
        if (!sourceRows.isEmpty()) {
            log.warn("job.source_config_names empty, derive from SOURCE rows");
            return sourceRows.stream().map(MatterProgressConfigRow::configName).toList();
        }
        return List.of();
    }

    private String buildMcpTaskInstructions(WeeklyComparisonJob job, boolean readOutputFeishuDocUrl) {
        return INSTRUCTIONS_TEMPLATE
                .replace("{{weeklyComparisonFeishuAppId}}", weeklyComparisonFeishuAppId)
                .replace("{{outputDocTitle}}", formatTitle(job.outputDocTitleTpl()))
                .replace("{{outputConfigName}}", nullToEmpty(job.outputConfigName()))
                .replace("{{oabpSchema}}", defaultOabpSchema);
    }

    private static String loadClasspathUtf8(String resourcePath) {
        try (InputStream in = OpenClawMcpWeeklyComparisonDelegate.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load " + resourcePath, e);
        }
    }

    private String buildMinuteQuerySql(WeeklyComparisonJob job) {
        String type = job.minuteQueryType() != null ? job.minuteQueryType().trim().toUpperCase() : "";
        try {
            JsonNode params = objectMapper.readTree(
                    job.minuteQueryParamsJson() != null && !job.minuteQueryParamsJson().isBlank()
                            ? job.minuteQueryParamsJson() : "{}");
            return switch (type) {
                case "PRESET_LAST_7_DAYS" -> {
                    int preset = params.path("presetTypeCode").asInt(1);
                    int days = Math.max(1, params.path("days").asInt(7));
                    yield """
                            SELECT m.meeting_id, im.title, m.preset_type_code, m.generated_at,
                                   m.content_markdown, m.content_url, m.generation_status
                            FROM int_meeting_minute m
                            LEFT JOIN int_meeting im ON im.id = m.meeting_id
                            WHERE m.preset_type_code = %d
                              AND m.generation_status = 'READY'
                              AND m.generated_at >= DATE_SUB(NOW(), INTERVAL %d DAY)
                            ORDER BY m.generated_at DESC
                            """.formatted(preset, days);
                }
                case "MEETING_IDS" -> {
                    StringBuilder ids = new StringBuilder();
                    JsonNode arr = params.path("meetingIds");
                    if (arr.isArray()) {
                        for (JsonNode n : arr) {
                            String id = n.asText("").trim();
                            if (id.isEmpty()) {
                                continue;
                            }
                            if (ids.length() > 0) {
                                ids.append(", ");
                            }
                            ids.append('\'').append(id.replace("'", "''")).append('\'');
                        }
                    }
                    if (ids.isEmpty()) {
                        yield "-- MEETING_IDS but minute_query_params.meetingIds is empty";
                    }
                    yield """
                            SELECT m.meeting_id, im.title, m.preset_type_code, m.generated_at,
                                   m.content_markdown, m.content_url, m.generation_status
                            FROM int_meeting_minute m
                            LEFT JOIN int_meeting im ON im.id = m.meeting_id
                            WHERE m.meeting_id IN (%s)
                              AND m.generation_status = 'READY'
                            ORDER BY m.generated_at DESC
                            """.formatted(ids);
                }
                default -> "-- unknown minuteQueryType: " + job.minuteQueryType();
            };
        } catch (Exception e) {
            return "-- failed to parse minuteQueryParams: " + e.getMessage();
        }
    }

    private String compactJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        return raw.replace("\n", "").replace("\r", "").trim();
    }

    private static String formatTitle(String tpl) {
        String t = tpl != null && !tpl.isBlank() ? tpl : "\u4e8b\u9879\u5bf9\u6bd4\u901a\u62a5-{date}";
        return t.replace("{date}", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static int indexOfUrlTerminator(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == ',' || c == '}') {
                return i;
            }
        }
        return -1;
    }

    static String extractReportUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int key = text.indexOf("generatedReportUrl=");
        if (key >= 0) {
            String tail = text.substring(key + "generatedReportUrl=".length()).trim();
            int end = indexOfUrlTerminator(tail);
            String candidate = end > 0 ? tail.substring(0, end) : tail;
            if (candidate.startsWith("http")) {
                return candidate;
            }
        }
        Matcher m = FEISHU_REPORT_URL.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    public record McpWeeklyComparisonResult(
            boolean success,
            String replyText,
            com.smartmeeting.matterprogress.model.ParsedComparisonItems parsedItems,
            String errorMessage) {

        static McpWeeklyComparisonResult ok(String reply, com.smartmeeting.matterprogress.model.ParsedComparisonItems parsed) {
            return new McpWeeklyComparisonResult(true, reply, parsed, null);
        }

        static McpWeeklyComparisonResult failed(String error) {
            return new McpWeeklyComparisonResult(false, null, null, error);
        }
    }
}
