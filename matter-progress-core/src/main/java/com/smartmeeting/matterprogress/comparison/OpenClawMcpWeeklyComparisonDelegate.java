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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weekly comparison OpenClaw full-delegate: WS dispatch; Agent uses meeting-mysql for oabp SOURCE + minutes.
 * Bot parses {@code BEGIN/END_WEEKLY_COMPARISON_ITEMS} JSON and JDBC writeback.
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

        int sqlSourceCount = rowsWithOabpTaskSql(sourceRows).size();
        log.info("OpenClaw MCP weekly-comparison WS: jobId={} taskId={} runKey={} sessionKey={} gatewayUrl={} sources={} sqlSources={}",
                job.id(), taskId, runKey, sessionKeyForTask, gatewayUrl, sourceRows.size(), sqlSourceCount);
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
            log.info("OpenClaw MCP weekly-comparison done: jobId={} replyLength={} items={} discarded={} runId={} success={}",
                    job.id(), text.length(), parsed.items().size(), parsed.discardedCount(),
                    parsed.runId(), parsed.success());
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
        if (job.outputConfigName() == null || job.outputConfigName().isBlank()) {
            return "job.output_config_name 为空";
        }
        String minuteType = job.minuteQueryType();
        if (minuteType == null || minuteType.isBlank()) {
            return "job.minute_query_type 为空";
        }
        // v0.30+ MCP 固定三表 SOURCE，不再要求 preset oabpTaskSql 或 sourceRows 非空
        return null;
    }

    static List<MatterProgressConfigRow> rowsWithOabpTaskSql(List<MatterProgressConfigRow> sourceRows) {
        if (sourceRows == null || sourceRows.isEmpty()) {
            return List.of();
        }
        return sourceRows.stream().filter(MatterProgressConfigRow::hasOabpTaskSql).toList();
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

        sb.append("[source_config_names]\n");
        sb.append("oabp-pro-todos\n");

        sb.append("[oabp_todos_sql]\n");
        sb.append(buildOabpTodosSqlBlock());

        outputRow.ifPresent(out -> {
            String outputConfigName = out.configName() != null && !out.configName().isBlank()
                    ? out.configName()
                    : nullToEmpty(job.outputConfigName());
            if (outputConfigName != null && !outputConfigName.isBlank()) {
                sb.append("[output_reference_sql]\n");
                sb.append("config=").append(outputConfigName).append('\n');
                sb.append("sql=").append(buildOutputReferenceSql(outputConfigName)).append('\n');
            }
        });

        sb.append("[minute_query_params]\n");
        sb.append("type=").append(nullToEmpty(job.minuteQueryType())).append('\n');
        sb.append("params=").append(compactJson(job.minuteQueryParamsJson())).append('\n');

        sb.append("[minute_query_sql]\n");
        sb.append(buildMinuteQuerySql(job)).append('\n');

        sb.append(buildRunInsertPayload(job, outputRow));

        sb.append("[mysql_target]\n");
        sb.append("hint=meeting-mysql MCP: minutes use database=intelligence; SOURCE SQL runs against oabp schema\n");
        sb.append("database=intelligence\n");
        sb.append("oabp_schema=").append(defaultOabpSchema).append('\n');
        sb.append("tables=int_meeting_minute,int_matter_progress_doc_config,")
          .append(defaultOabpSchema).append(".jq_todos_task,")
          .append(defaultOabpSchema).append(".jq_todos_subtask,")
          .append(defaultOabpSchema).append(".jq_todos_task_followup\n");
        sb.append("oabp_note=Execute [oabp_todos_sql] three-table SELECT as-is (preferred SOURCE); "
                + "tables are qualified with oabp_schema e.g. ").append(defaultOabpSchema)
          .append(".jq_todos_task.\n");

        sb.append("-----END_WEEKLY_COMPARISON_DATA-----");
        return sb.toString();
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

    private String buildRunInsertPayload(WeeklyComparisonJob job, Optional<MatterProgressConfigRow> outputRow) {
        Integer presetTypeCode = outputRow.map(MatterProgressConfigRow::presetTypeCode).orElse(null);
        Integer agendaIndex = outputRow.map(MatterProgressConfigRow::agendaIndex).orElse(null);
        String outputConfigName = outputRow
                .map(MatterProgressConfigRow::configName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(nullToEmpty(job.outputConfigName()));
        String title = formatTitle(job.outputDocTitleTpl());
        StringBuilder sb = new StringBuilder();
        sb.append("[run_insert_payload]\n");
        sb.append("table=int_weekly_matter_comparison_run\n");
        sb.append("columns=job_id,output_config_name,preset_type_code,agenda_index,title,")
          .append("item_count,generation_status,generated_at,run_error\n");
        sb.append("values=")
          .append(job.id()).append(',')                              // job_id (long)
          .append(sqlStringLiteral(outputConfigName)).append(',')    // output_config_name
          .append(presetTypeCode != null ? presetTypeCode : "NULL").append(',')  // preset_type_code
          .append(agendaIndex != null ? agendaIndex : "NULL").append(',')        // agenda_index
          .append(sqlStringLiteral(title)).append(',')              // title
          .append("0,").append(sqlStringLiteral("READY")).append(',')  // item_count + generation_status (placeholder)
          .append("NOW(),NULL\n");                                  // generated_at + run_error
        sb.append("note=Agent 组装 INSERT 时直接使用本行 values；item_count 占位 0、generation_status 占位 READY，")
          .append("Bot 入库校验后会 UPDATE 回填真实值。generated_at 用 NOW() 取写库时刻。\n");
        return sb.toString();
    }

    /** SQL 字符串字面量：null → NULL；非空 → 单引号包裹且内部单引号转义为两个单引号。 */
    private static String sqlStringLiteral(String s) {
        if (s == null) {
            return "NULL";
        }
        return "'" + s.replace("'", "''") + "'";
    }

    private String buildOabpTodosSqlBlock() {
        String s = defaultOabpSchema;
        StringBuilder sb = new StringBuilder();
        // 1. 主任务表 jq_todos_task（含决策人昵称）
        sb.append("config=oabp-pro-todos\n");
        sb.append("schema=").append(s).append('\n');
        sb.append("section=main_task\n");
        sb.append("sql=").append("""
SELECT
  t.id                                          AS task_id,
  t.task_name,
  t.business_block,
  t.project_id,
  t.progress,
  CASE t.status
    WHEN 0 THEN '未开始'
    WHEN 1 THEN '进行中'
    WHEN 2 THEN '已完成'
    WHEN 3 THEN '已延期'
  END                                            AS status_label,
  t.status                                       AS status_raw,
  t.start_date,
  t.planned_end_date,
  t.remark,
  t.task_detail,
  t.create_time,
  t.update_time,
  dm.nickname                                    AS decision_maker_name
FROM %s.jq_todos_task t
LEFT JOIN %s.system_users dm
  ON dm.id = t.decision_maker_user_id
  AND (dm.deleted = 0 OR dm.deleted IS NULL)
WHERE (t.deleted = 0 OR t.deleted IS NULL)
ORDER BY t.planned_end_date ASC
""".formatted(s, s).strip()).append('\n');
        sb.append("---\n");
        // 2. 子任务表 jq_todos_subtask（含执行人昵称）
        sb.append("config=oabp-pro-todos\n");
        sb.append("schema=").append(s).append('\n');
        sb.append("section=subtask\n");
        sb.append("sql=").append("""
SELECT
  s.id          AS subtask_id,
  s.parent_id   AS task_id,
  s.task_name   AS subtask_name,
  s.asignee_id,
  u.nickname    AS assignee_name,
  s.remark      AS subtask_remark,
  s.update_time
FROM %s.jq_todos_subtask s
LEFT JOIN %s.system_users u
  ON u.id = s.asignee_id
  AND (u.deleted = 0 OR u.deleted IS NULL)
WHERE (s.deleted = 0 OR s.deleted IS NULL)
ORDER BY s.parent_id, s.id
""".formatted(s, s).strip()).append('\n');
        sb.append("---\n");
        // 3. 跟进明细 jq_todos_task_followup（每任务最新一条）
        sb.append("config=oabp-pro-todos\n");
        sb.append("schema=").append(s).append('\n');
        sb.append("section=followup_latest\n");
        sb.append("sql=").append("""
SELECT f.task_id, f.task_type, f.followup_content, f.last_week_progress,
       f.this_week_plan, f.report_date, f.creator, f.update_time
FROM %s.jq_todos_task_followup f
WHERE (f.deleted = 0 OR f.deleted IS NULL)
  AND f.id = (
    SELECT MAX(f2.id)
    FROM %s.jq_todos_task_followup f2
    WHERE f2.task_id = f.task_id
      AND (f2.deleted = 0 OR f2.deleted IS NULL)
  )
ORDER BY f.task_id
""".formatted(s, s).strip()).append('\n');
        return sb.toString();
    }

    private String buildOutputReferenceSql(String outputConfigName) {
        String escaped = outputConfigName.replace("'", "''");
        return """
                SELECT i.category, i.matter_name, i.assignee, i.time_node, i.status_label, i.sort_order
                FROM int_weekly_matter_comparison_item i
                INNER JOIN (
                  SELECT id
                  FROM int_weekly_matter_comparison_run
                  WHERE output_config_name = '%s'
                  ORDER BY generated_at DESC, id DESC
                  LIMIT 1
                ) latest ON latest.id = i.run_id
                ORDER BY FIELD(i.category, 'DELAYED', 'COMPLETED', 'IN_PROGRESS'), i.sort_order, i.id
                """.formatted(escaped);
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
