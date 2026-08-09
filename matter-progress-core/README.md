# matter-progress-core

会前事项对比通报的 **纯 Java 核心库**（`com.smartmeeting:matter-progress-core:0.1.0`）。

- **不是** Spring Boot 应用，**没有** `application.yml`
- **由** `feishu-scheduled-bot` 通过 `MatterProgressCoreConfiguration` 装配并运行
- **`meeting-server` 不依赖** 本 jar，仅 JDBC 只读 `generated_report_url`

## 在整体架构中的位置

```text
触发（Bot）                    业务（本 jar）                 展示（meeting-server）
─────────────────────────────────────────────────────────────────────────────
Quartz / POST …/execute  →  WeeklyMatterComparisonService  →  host_state 只读链接
WeeklyComparisonScheduleService      ↓ JDBC
WeeklyMatterComparisonJob            MySQL 共库（preset host_agenda v2 + oabp）
```

详述：[docs/weekly-matter-comparison.md](../docs/weekly-matter-comparison.md) · [meeting USER-MANUAL §1.4](../docs/USER-MANUAL.md) · [Bot USER-MANUAL §12.0](../../feishu-scheduled-bot/docs/USER-MANUAL.md)

## 执行路径（何时走哪条）

生产默认 **MCP 全托管**；MCP 失败时可回退 **Legacy**（Java 查 oabp + LLM + 写 Doc）。配置由 Bot 注入，见 `feishu.weekly-comparison.openclaw.*`。

| 条件 | 路径 | SOURCE | 报告生成 |
|------|------|--------|----------|
| `delegate-to-mcp=true`（默认）且 MCP 成功且 Agent 回传 `runId` | **MCP · Agent 写库** | Agent `meeting-mysql` 执行 `[oabp_todos_sql]` 三表 SELECT + `[minute_query_sql]` 纪要 | Agent 自行 INSERT `int_weekly_matter_comparison_run/item`（§5），回传 `runId`；Bot 仅 count 校验 + 回写 `host_agenda`/`job` |
| `delegate-to-mcp=true` 且 MCP 成功但 Agent 未回传 `runId`（写库失败/降级） | **MCP · Bot 入库** | 同上 | Agent 输出 items JSON 块（无 `runId`），Bot 走 `persistBotWrittenRun` 自行 INSERT run+item |
| 上项失败且 `legacy-fallback-on-mcp-failure=true`（默认） | **Legacy fallback** | `OabpSourceQuery` 直连 oabp | `LlmComparisonReportGenerator` → Bot 入库 |
| `delegate-to-mcp=false` | **Legacy** | 同上 | 同上 |

**SOURCE 要求（v0.30+）**：MCP 路径下 Agent 直读 `oabp_pro` 三张待办表（SQL 由 `OpenClawMcpWeeklyComparisonDelegate.buildOabpTodosSqlBlock()` 固定生成并注入 `oabp_schema`），**不再**依赖父会序 `host_agenda.items[].oabpTaskSql`；preset `oabpTaskSql` 仅 Legacy 路径使用。**不再**读飞书 SOURCE `feishuDocUrl`。

**Agent 写库（v0.31+）**：MCP 路径下 Agent 用 `meeting-mysql__mysql_query` 直接 INSERT run+item 两表（模板 §5 写库协议），Bot 据 `parsed.runId` 分流：`agentWroteRun()=true` → `persistAgentWrittenRun`（count 校验 + UPDATE run 状态 + 回写 `host_agenda`/`job`）；否则 → `persistBotWrittenRun`（Bot 自行入库，旧模式兼容）。前置依赖：`meeting-mysql` MCP 账号对两表 INSERT 权限 + `LAST_INSERT_ID()` 连接复用（见 Gateway 运维清单）。

Gateway 运维清单：[mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md](../mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md)

## 核心类

| 类 | 职责 |
| ---- | ---- |
| `WeeklyMatterComparisonService` | **Facade**：`runJob(id)` 编排整条流水线 |
| `OpenClawMcpWeeklyComparisonDelegate` | MCP 路径：WS 下发 `[oabp_todos_sql]` 三表 SQL + `[minute_query_sql]` 纪要 SQL + `[run_insert_payload]` 写库字段 + 指令模板 |
| `OabpSourceQuery` | Legacy：oabp 只读 SQL → Markdown 表格 |
| `JdbcWeeklyComparisonJobRepository` | 读/写 `int_weekly_matter_comparison_job`、`last_run_*` |
| `JdbcPresetHostAgendaConfigRepository` | 扫描 preset `host_agenda` v2；绑定 `oabpTaskSql` |
| `JdbcMeetingMinuteQuery` | `PRESET_LAST_7_DAYS` / `MEETING_IDS` → READY 纪要 |
| `JdbcWeeklyComparisonRunRepository` | 写 `int_weekly_matter_comparison_run`；`countItemsByRunId` 校验 Agent 写库 |
| `JdbcWeeklyComparisonItemRepository` | 写 `int_weekly_matter_comparison_item`（Bot 入库分支用） |
| `WeeklyComparisonItemsJsonParser` | 解析 `BEGIN/END_WEEKLY_COMPARISON_ITEMS` JSON 块；提取 `runId` + items |
| `RestFeishuDocClient` | tenant token；**写**通报 Doc（Legacy）；可选读 OUTPUT 参考 Doc |
| `LlmComparisonReportGenerator` | OpenAI 兼容 Chat；Key 空/`test`/失败 → fallback（Legacy） |

## `runJob` 流水线

1. 加载 job，`enabled=0` → 失败  
2. 校验 SOURCE（`validateSourceRows`）  
3. **MCP**：`OpenClawMcpWeeklyComparisonDelegate` → WS 下发 `[oabp_todos_sql]` + `[minute_query_sql]` + `[run_insert_payload]` + 指令模板 → Agent 查三表+纪要 → 归并 → INSERT run+item → 回传 `runId` + items JSON 块  
4. **入库分流**（`persistRunAndItems`）：
   - `parsed.agentWroteRun()` → `persistAgentWrittenRun`：`countItemsByRunId` 校验 → `updateStatus` 回填 → `writeGeneratedReportRun` + `updateRunResult`
   - 否则 → `persistBotWrittenRun`：`insertRun` + `batchInsert` + `updateStatus` → `writeGeneratedReportRun` + `updateRunResult`
5. **Legacy / fallback**：`collectSourceData` → `resolveMinutes` → LLM → `persistBotWrittenRun`  
6. `jobRepository.updateRunResult(SUCCESS|FAILED)`

## 可配置项（本 jar 无配置文件）

### Bot 注入（`MatterProgressCoreConfiguration`）

| Bot 配置 / 环境变量 | 注入目标 | 说明 |
| ------------------- | -------- | ---- |
| `feishu.weekly-comparison.openclaw.delegate-to-mcp` | `WeeklyMatterComparisonService` | 默认 `true` → MCP |
| `feishu.weekly-comparison.openclaw.legacy-fallback-on-mcp-failure` | 同上 | MCP 失败走 Legacy |
| `feishu.weekly-comparison.oabp.enabled` / `MEETING_DB_OABP_ENABLED` | `OabpSourceQuery` | Legacy 直连 oabp |
| `feishu.weekly-comparison.oabp.schema-name` / `DB_OABP_NAME` | Repository / MCP prompt | 默认 `oabp_pro` |
| `feishu.weekly-comparison.llm.*` / `MEETING_LLM_*` | `LlmComparisonReportGenerator` | Legacy LLM |
| `feishu.weekly-comparison.app-id` / `app-secret` | `RestFeishuDocClient` | 写通报 Doc |

### 数据库（运维）

| 表 / 字段 | 说明 |
| ---- | ---- |
| `int_weekly_matter_comparison_job` | 任务定义、`source_config_names` |
| `int_meeting_type_preset.host_agenda` v2 | `items[].oabpTaskSql` + `docs[]` configName/role |

## 构建与发布

```bash
cd smart-meeting-java
mvn -pl matter-progress-core install
```

修改 core 后：**install → 重启 feishu-scheduled-bot**；**无需**重启 meeting-server。

## 约束

- **不**发送飞书 IM（不写 PushLog）
- **不**覆盖 `feishu_doc_url`
- **不**读飞书多维表/docx 作为 SOURCE（仅 oabp SQL）
- **MCP 路径**需 meeting-mysql 账号具备 oabp **SELECT** 权限 + `intelligence.int_weekly_matter_comparison_run/item` **INSERT** 权限
- **MCP 路径**需 `meeting-mysql__mysql_query` 支持 `LAST_INSERT_ID()`（连接复用）；若不复用，Agent 用 `SELECT id FROM run WHERE job_id=? ORDER BY id DESC LIMIT 1` 兜底
