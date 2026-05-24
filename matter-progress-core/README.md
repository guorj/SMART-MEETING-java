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
WeeklyMatterComparisonJob            MySQL 共库
```

详述：[docs/weekly-matter-comparison.md](../docs/weekly-matter-comparison.md) · [meeting USER-MANUAL §1.4](../docs/USER-MANUAL.md) · [Bot USER-MANUAL §12.0](../../feishu-scheduled-bot/docs/USER-MANUAL.md)

## 核心类

| 类 | 职责 |
| ---- | ---- |
| `WeeklyMatterComparisonService` | **Facade**：`runJob(id)` 编排整条流水线 |
| `JdbcWeeklyComparisonJobRepository` | 读/写 `int_weekly_matter_comparison_job`、`last_run_*` |
| `JdbcMatterProgressConfigRepository` | 读 SOURCE/OUTPUT；`writeGeneratedReport` **仅**更新 `generated_report_*` |
| `JdbcMeetingMinuteQuery` | `PRESET_LAST_7_DAYS` / `MEETING_IDS` → READY 纪要 |
| `RestFeishuDocClient` | tenant token；读 docx/wiki/base 正文（多类型分派）；`createAndWriteMarkdown` |
| `LlmComparisonReportGenerator` | OpenAI 兼容 Chat；Key 空/`test`/失败 → fallback 列表 Markdown |

## `runJob` 流水线

1. 加载 job，`enabled=0` → 失败  
2. `collectSourceDocs`：`source_config_names` → 拉飞书正文；可选 OUTPUT 的 `feishu_doc_url`  
3. `resolveMinutes`：按 `minute_query_type` 查 `int_meeting_minute`  
4. `LlmComparisonReportGenerator.generate`  
5. **MCP 模式**（`delegate-to-mcp=true`）：`OpenClawMcpWeeklyComparisonDelegate` → WS → Skill+MCP 读/写；Java 仅审计写回  
   **Legacy**：`FeishuDocClient.createAndWriteMarkdown`  
6. `configRepository.writeGeneratedReport(outputConfigName, url, now)`  
7. `jobRepository.updateRunResult(SUCCESS|FAILED)`

## 可配置项（本 jar 无配置文件）

配置由 **Bot YAML** 或 **MySQL** 注入，见下表。

### Bot 注入（`MatterProgressCoreConfiguration`）

| Bot 配置 / 环境变量 | 注入目标 | 说明 |
| ------------------- | -------- | ---- |
| `feishu.weekly-comparison.read-output-feishu-doc-url` | `WeeklyMatterComparisonService` | 是否读 OUTPUT 行 `feishu_doc_url` |
| `feishu.weekly-comparison.llm.*` / `MEETING_LLM_*` | `LlmComparisonReportGenerator` | 对比 LLM；`test` → fallback |
| `feishu.app-id` / `feishu.app-secret` / `feishu.domain` | `RestFeishuDocClient` | 飞书 Doc 读写的应用凭证 |

### 数据库（运维 SQL）

| 表 | 字段 | 说明 |
| ---- | ---- | ---- |
| `int_weekly_matter_comparison_job` | `cron_expression`、`source_config_names`、`minute_query_*`、`output_*` | 任务定义 |
| `int_matter_progress_doc_config` | SOURCE：`feishu_doc_url`；OUTPUT：接收写回 | 与主持页 preset/agenda 绑定 |

### 代码内常量（改需发版 jar）

| 行为 | 位置 |
| ---- | ---- |
| 单份资料/纪要截断 8000 字 | `LlmComparisonReportGenerator` |
| LLM temperature / max_tokens | 同上 |
| docx/wiki/base 均已支持 | `RestFeishuDocClient`：wiki 先 get_node 解析 obj_type 再递归；base 用 records/search + WrongTableId 纠正 |

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
- **不**使用 OpenClaw Gateway（对比路径为直调 LLM）