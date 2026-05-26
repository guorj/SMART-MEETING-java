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
WeeklyMatterComparisonJob            MySQL 共库（preset host_agenda v2）
```

详述：[docs/weekly-matter-comparison.md](../docs/weekly-matter-comparison.md) · [meeting USER-MANUAL §1.4](../docs/USER-MANUAL.md) · [Bot USER-MANUAL §12.0](../../feishu-scheduled-bot/docs/USER-MANUAL.md)

## 执行路径（何时走哪条）

生产默认 **MCP 全托管**；MCP 失败时可回退 **Legacy**（Java 拉数 + LLM + 写 Doc）。配置由 Bot 注入，见 `feishu.weekly-comparison.openclaw.*`。

| 条件 | 路径 | 飞书读/写 | 报告生成 |
|------|------|-----------|----------|
| `delegate-to-mcp=true`（默认）且 MCP 成功 | **MCP** | Agent + `lark-mcp`（Gateway **须** `tenant_access_token`） | Skill + MCP |
| 上项失败且 `legacy-fallback-on-mcp-failure=true`（默认） | **Legacy fallback** | `RestFeishuDocClient`（tenant） | `LlmComparisonReportGenerator` |
| `delegate-to-mcp=false` | **Legacy** | 同上 | 同上 |

**已移除（2026-05）**：Legacy 内的 `OpenClawComparisonReportGenerator`（向 Gateway 传已拉正文的第二条 WS 路径）。OpenClaw 集成仅保留 **MCP Delegate**（`OpenClawMcpWeeklyComparisonDelegate`）。

Gateway 运维清单：[mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md](../mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md)

## 核心类

| 类 | 职责 |
| ---- | ---- |
| `WeeklyMatterComparisonService` | **Facade**：`runJob(id)` 编排整条流水线 |
| `OpenClawMcpWeeklyComparisonDelegate` | MCP 路径：WS 下发 Skill，不传 Java 预读正文 |
| `JdbcWeeklyComparisonJobRepository` | 读/写 `int_weekly_matter_comparison_job`、`last_run_*` |
| `JdbcPresetHostAgendaConfigRepository` | 扫描 preset `host_agenda` v2；`writeGeneratedReport` 更新 JSON 内 `generatedReportUrl/At` |
| `JdbcMeetingMinuteQuery` | `PRESET_LAST_7_DAYS` / `MEETING_IDS` → READY 纪要 |
| `RestFeishuDocClient` | tenant token；读 docx/wiki/base 正文；`createAndWriteMarkdown`（Legacy） |
| `LlmComparisonReportGenerator` | OpenAI 兼容 Chat；Key 空/`test`/失败 → fallback 列表 Markdown（Legacy） |

## `runJob` 流水线

1. 加载 job，`enabled=0` → 失败  
2. **MCP**（`delegate-to-mcp=true`）：`OpenClawMcpWeeklyComparisonDelegate` → WS → Skill + `lark-mcp` + `meeting-mysql`；Java 仅解析 `generatedReportUrl=` 并 JDBC 写回  
3. **Legacy / fallback**：`collectSourceDocs` → `resolveMinutes` → `LlmComparisonReportGenerator` → `FeishuDocClient.createAndWriteMarkdown`  
4. `configRepository.writeGeneratedReport(outputConfigName, url, now)`  
5. `jobRepository.updateRunResult(SUCCESS|FAILED)`

## 可配置项（本 jar 无配置文件）

配置由 **Bot YAML** 或 **MySQL** 注入，见下表。

### Bot 注入（`MatterProgressCoreConfiguration`）

| Bot 配置 / 环境变量 | 注入目标 | 说明 |
| ------------------- | -------- | ---- |
| `feishu.weekly-comparison.openclaw.delegate-to-mcp` | `WeeklyMatterComparisonService` | 默认 `true` → MCP |
| `feishu.weekly-comparison.openclaw.legacy-fallback-on-mcp-failure` | 同上 | 默认 `true` → MCP 失败走 Legacy LLM |
| `feishu.weekly-comparison.read-output-feishu-doc-url` | 同上 | 是否读 OUTPUT 行 `feishu_doc_url`（Legacy） |
| `feishu.weekly-comparison.llm.*` / `MEETING_LLM_*` | `LlmComparisonReportGenerator` | Legacy 对比 LLM；`test` → fallback |
| `feishu.weekly-comparison.app-id` / `app-secret` | `RestFeishuDocClient` | Legacy 飞书 tenant 读写 |

### 数据库（运维 SQL）

| 表 / 字段 | 说明 |
| ---- | ---- |
| `int_weekly_matter_comparison_job` | 任务定义、`source_config_names`、`minute_query_*`、`output_*` |
| `int_meeting_type_preset.host_agenda` v2 | `items[].docs[]`：`configName`、`feishuDocUrl`、`generatedReportUrl`；**不再使用** `int_matter_progress_doc_config` |

### 代码内常量（改需发版 jar）

| 行为 | 位置 |
| ---- | ---- |
| 单份资料/纪要截断 8000 字 | `LlmComparisonReportGenerator` |
| LLM temperature / max_tokens | 同上 |
| docx/wiki/base 均已支持 | `RestFeishuDocClient` |

## 构建与发布

```bash
cd smart-meeting-java
mvn -pl matter-progress-core install
```

Bot 侧 `pom.xml` 依赖：

```xml
<dependency>
  <groupId>com.smartmeeting</groupId>
  <artifactId>matter-progress-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

修改 core 后：**install → 重启 feishu-scheduled-bot**；**无需**重启 meeting-server。

## 约束

- **不**发送飞书 IM（不写 PushLog）
- **不**覆盖 `feishu_doc_url`
- **不**在会中实时调用
- **MCP 路径**依赖 OpenClaw Gateway + `lark-mcp`；**Legacy** 仅用 Java `tenant_access_token` + LLM
