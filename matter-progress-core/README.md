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
| `delegate-to-mcp=true`（默认）且 MCP 成功 | **MCP** | Agent `meeting-mysql` 执行 `oabpTaskSql` | Skill + lark-mcp 写 Doc |
| 上项失败且 `legacy-fallback-on-mcp-failure=true`（默认） | **Legacy fallback** | `OabpSourceQuery` 直连 oabp | `LlmComparisonReportGenerator` |
| `delegate-to-mcp=false` | **Legacy** | 同上 | 同上 |

**SOURCE 要求**：父会序 `host_agenda.items[].oabpTaskSql` 非空；**不再**读飞书 SOURCE `feishuDocUrl`。

Gateway 运维清单：[mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md](../mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md)

## 核心类

| 类 | 职责 |
| ---- | ---- |
| `WeeklyMatterComparisonService` | **Facade**：`runJob(id)` 编排整条流水线 |
| `OpenClawMcpWeeklyComparisonDelegate` | MCP 路径：WS 下发 oabp SQL + 纪要 SQL |
| `OabpSourceQuery` | Legacy：oabp 只读 SQL → Markdown 表格 |
| `JdbcWeeklyComparisonJobRepository` | 读/写 `int_weekly_matter_comparison_job`、`last_run_*` |
| `JdbcPresetHostAgendaConfigRepository` | 扫描 preset `host_agenda` v2；绑定 `oabpTaskSql` |
| `JdbcMeetingMinuteQuery` | `PRESET_LAST_7_DAYS` / `MEETING_IDS` → READY 纪要 |
| `RestFeishuDocClient` | tenant token；**写**通报 Doc（Legacy）；可选读 OUTPUT 参考 Doc |
| `LlmComparisonReportGenerator` | OpenAI 兼容 Chat；Key 空/`test`/失败 → fallback（Legacy） |

## `runJob` 流水线

1. 加载 job，`enabled=0` → 失败  
2. 校验 SOURCE 均有 `oabpTaskSql`（fail-fast）  
3. **MCP**：`OpenClawMcpWeeklyComparisonDelegate` → WS → Agent 查 oabp + 纪要 → lark-mcp 写 Doc  
4. **Legacy / fallback**：`collectSourceData` → `resolveMinutes` → LLM → `createAndWriteMarkdown`  
5. `configRepository.writeGeneratedReport(outputConfigName, url, now)`  
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
- **MCP 路径**需 meeting-mysql 账号具备 oabp **SELECT** 权限
