# 会前事项对比通报（Weekly Matter Comparison）

本文档是 **v0.9** 架构的权威说明：会中不再实时调用 OpenClaw 生成通报，改由 **feishu-scheduled-bot** 在会前定时对比飞书资料与会议纪要，写回数据库；**meeting-server** 主持页只读展示链接。

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────────────────────────────┐
│  feishu-scheduled-bot（Quartz，默认周一 10:00 Asia/Shanghai）              │
│  matter-progress-core :: WeeklyMatterComparisonService                    │
│    ① 读 SOURCE config_name → feishu_doc_url → 拉取飞书正文               │
│    ② 读 int_meeting_minute（PRESET_LAST_7_DAYS 或 MEETING_IDS）          │
│    ③ LLM 生成对比 Markdown（无 Key 时用 fallback 模板）                    │
│    ④ 创建飞书 Doc → 写回 OUTPUT 行 generated_report_url / _at            │
│    （不写群通知；不覆盖 feishu_doc_url）                                   │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ MySQL 共库（如 intelligence）
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  int_matter_progress_doc_config                                           │
│    config_role=SOURCE  → 合并 host 会序飞书外链（PresetAgendaDocService）   │
│    config_role=OUTPUT  → bot 写回 generated_report_url                   │
│    config_role=BOTH    → 可同时作 SOURCE + 写回（少见）                   │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ 主持页 WS host_state
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  meeting-server :: MeetingHostSessionService.buildStateNode               │
│    findReportBindingForAgenda(preset, agendaIndex)                        │
│    → generatedReportUrl / generatedReportAt / outputFeishuDocUrl          │
│  host-meeting.html 左侧「会序参考资料」只读双链                             │
└─────────────────────────────────────────────────────────────────────────┘
```

**已移除（勿再配置）**

| 旧能力 | 说明 |
|--------|------|
| `AgendaBriefingService` | 会序 RUNNING 时 OpenClaw 异步生成 Markdown |
| `host_state.briefingMarkdown` / `briefingStatus` | 前端 loading/ready/failed 通报 UI |
| `meeting.host.agenda-briefing.*` | 配置项已无效（代码已删） |
| 会序通报 TTS | `AgendaBriefingTtsScriptBuilder` 已删 |

**保留**

| 组件 | 用途 |
|------|------|
| `AgendaBriefingMarkdownValidator` | OpenClaw 纪要增强路径仍用于拒绝 JSON 串台 |

### 1.1 matter-progress-core 与 Bot 壳层

| 层 | 仓库 | 运行形态 | 职责 |
| ---- | ---- | -------- | ---- |
| **core** | `smart-meeting-java/matter-progress-core` | jar | 对比流水线、JDBC、飞书 Doc、LLM |
| **Bot 壳** | `feishu-scheduled-bot` | Spring Boot :8764 | Quartz 同步、`WeeklyMatterComparisonJob`、HTTP `/execute`、Spring 装配 |
| **读侧** | `meeting-server` | Spring Boot :8765 | **不依赖 core**；`PresetAgendaDocService` 只读 DB |

Bot 装配类：`com.example.feishubot.config.MatterProgressCoreConfiguration`。  
用户手册：[USER-MANUAL §1.4](USER-MANUAL.md)、[Bot §12.0](../../feishu-scheduled-bot/docs/USER-MANUAL.md)、[matter-progress-core/README.md](../matter-progress-core/README.md)。

---

## 2. 数据库

### 2.1 DDL 执行顺序

```bash
# meeting-server 资源目录（加列 + job 表）
schema-upgrade/v0.9-weekly-matter-comparison.sql

# 示例种子（SOURCE 角色 + OUTPUT 行 + 示例 job）
schema-seed/v0.9-weekly-matter-comparison-seed.sql

# v0.10：移除 legacy openclaw_briefing 列（幂等）
schema-upgrade/v0.10-drop-openclaw-briefing.sql
```

Bot 侧 Flyway：`feishu-scheduled-bot/.../db/migration/V5__weekly_matter_comparison_job.sql`（仅 job 表；配置表列在 meeting DDL 中）。

### 2.2 `int_matter_progress_doc_config` 新增列

| 列 | 类型 | 说明 |
|----|------|------|
| `config_role` | `VARCHAR(16)` 默认 `SOURCE` | `SOURCE`：合并议程飞书链接；`OUTPUT`/`BOTH`：写回通报链接 |
| `generated_report_url` | `VARCHAR(2000)` NULL | **仅 bot 写**；主持页只读展示 |
| `generated_report_at` | `DATETIME` NULL | 最近一次 bot 成功写回时间 |

**写回规则**

- bot **只 UPDATE** `generated_report_url`、`generated_report_at`。
- **永不 UPDATE** `feishu_doc_url`（SOURCE 资料地址与 OUTPUT 补充资料分离）。

### 2.3 `int_weekly_matter_comparison_job`

| 列 | 说明 |
|----|------|
| `job_name` | 全局唯一，Quartz JobKey 字符串化 id |
| `cron_expression` | 5 或 6 段 Cron；默认 `0 10 * * MON` |
| `schedule_timezone` | 默认 `Asia/Shanghai` |
| `source_config_names` | JSON 数组，指向 `config_name`（需有有效 `feishu_doc_url`） |
| `minute_query_type` | `PRESET_LAST_7_DAYS` 或 `MEETING_IDS` |
| `minute_query_params` | JSON，见 §4 |
| `output_config_name` | 写回目标行的 `config_name`（须为 OUTPUT/BOTH） |
| `output_doc_title_tpl` | 飞书 Doc 标题，支持 `{date}` |
| `feishu_folder_token` | 可选；创建 Doc 的目标文件夹 |
| `last_run_at` / `last_run_status` / `last_run_error` | 最近一次执行审计 |

---

## 3. 配置角色与主持页绑定

### 3.1 `config_role` 语义

| 角色 | 合并议程（SOURCE） | bot 写回 generated_report_url | 主持页 outputFeishuDocUrl |
|------|-------------------|------------------------------|---------------------------|
| `SOURCE` | 是 | 否 | 否 |
| `OUTPUT` | 否 | 是 | 可选（同 row 的 `feishu_doc_url`） |
| `BOTH` | 是 | 是 | 否（BOTH 不写 outputFeishuDocUrl 分支） |

`PresetAgendaDocService.listEnabledByPreset()` **仅合并** `SOURCE`/`BOTH`，纯 `OUTPUT` 行不会污染议程飞书链接。

### 3.2 主持页 WS 字段（`host_state.topics[]`）

| 字段 | 来源 |
|------|------|
| `generatedReportUrl` | OUTPUT/BOTH 行的 `generated_report_url` |
| `generatedReportAt` | 同上 `generated_report_at`（ISO 字符串） |
| `outputFeishuDocUrl` | OUTPUT 行且 `feishu_doc_url` 非空时的补充资料链 |
| `feishuDocUrl` / `feishuDocs` | SOURCE 合并结果（不变） |

绑定键：`preset_type_code` + `agenda_index`（0-based，与会务 `host_agenda.items` 下标一致）。

---

## 4. Job 参数详解

### 4.1 `minute_query_type = PRESET_LAST_7_DAYS`

适用于 preset 1～5 模板会议（如综合管理会 weekly）。

```json
{
  "presetTypeCode": 1,
  "days": 7
}
```

SQL 逻辑（`JdbcMeetingMinuteQuery`）：`int_meeting_minute` 中 `preset_type_code` 匹配、`generation_status='READY'`、`generated_at >= now()-days`。

### 4.2 `minute_query_type = MEETING_IDS`

适用于非模板或指定场次：

```json
{
  "meetingIds": ["uuid-1", "uuid-2"]
}
```

### 4.3 完整 INSERT 示例

**参考脚本（含 SOURCE / OUTPUT / Job、多场景注释）：**  
`meeting-server/src/main/resources/schema-examples/v0.9-weekly-matter-comparison-insert.sql`

**幂等种子（生产已有 SOURCE 行时）：**  
`schema-seed/v0.9-weekly-matter-comparison-seed.sql`

最小 Job 示例：

```sql
INSERT INTO int_weekly_matter_comparison_job (
    job_name, enabled, cron_expression, schedule_timezone,
    source_config_names, minute_query_type, minute_query_params,
    output_config_name, output_doc_title_tpl
) VALUES (
    'preset1-comprehensive-weekly', 1, '0 10 * * MON', 'Asia/Shanghai',
    JSON_ARRAY(
        'preset1-comp-agenda-01', 'preset1-comp-agenda-02',
        'preset1-comp-agenda-03', 'preset1-comp-agenda-04'
    ),
    'PRESET_LAST_7_DAYS',
    JSON_OBJECT('presetTypeCode', 1, 'days', 7),
    'preset1-weekly-report-out',
    '综合管理会事项对比通报-{date}'
);
```

---

## 5. feishu-scheduled-bot 配置

> **联调总表**：[feishu-scheduled-bot USER-MANUAL §4.4](../../feishu-scheduled-bot/docs/USER-MANUAL.md) · **meeting 侧**：[USER-MANUAL §3.5–3.7](USER-MANUAL.md)

`application.yml` → `feishu.weekly-comparison.*`：

| 配置项 | 环境变量 | 默认 | 说明 |
|--------|----------|------|------|
| `sync-polling-enabled` | `FEISHU_WEEKLY_COMPARISON_SYNC` | `true` | DB↔Quartz 对账（组 `weekly-comparison-group`） |
| `read-output-feishu-doc-url` | `FEISHU_WEEKLY_COMPARISON_READ_OUTPUT_URL` | `true` | 对比时是否额外读取 OUTPUT 行 `feishu_doc_url` |
| `llm.api-url` | `MEETING_LLM_API_URL` | DeepSeek URL | 对比报告生成 |
| `llm.api-key` | `MEETING_LLM_API_KEY` | `test` | `test` 或空则走 fallback Markdown |
| `llm.model` | `MEETING_LLM_MODEL` | `deepseek-chat` | |

**API**

```http
POST /api/weekly-comparison/jobs/{id}/execute
X-API-Key: <FEISHU_API_KEY>
```

响应：`status=SUCCESS` 时含 `generatedReportUrl`；失败 `409` + `error`。

**Maven 依赖**

```xml
<dependency>
  <groupId>com.smartmeeting</groupId>
  <artifactId>matter-progress-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

部署前：`mvn -pl matter-progress-core install`（在 smart-meeting-java 根目录）。

---

## 6. 运维 Checklist

1. 执行 v0.9 DDL + seed（或手工 INSERT job / OUTPUT 行）。
2. 确认 `source_config_names` 每行 `enabled=1` 且 `feishu_doc_url` 有效。
3. 确认 `output_config_name` 存在，`config_role` 为 `OUTPUT` 或 `BOTH`，且 `preset_type_code` + `agenda_index` 与主持会序一致。
4. 配置 `MEETING_LLM_API_KEY`、飞书 `FEISHU_APP_ID/SECRET`；bot 与 meeting **共库**。
5. 启动 bot；可选 `POST .../execute` 试跑。
6. 验证：`SELECT generated_report_url FROM int_matter_progress_doc_config WHERE config_name='preset1-weekly-report-out'`。
7. 开 preset=1 会议，进入对应 `agenda_index` 会序，主持页左侧应出现「会前事项对比通报」链接。

---

## 7. 故障排查

| 现象 | 排查 |
|------|------|
| 主持页无通报链接 | OUTPUT 行是否存在；`generated_report_url` 是否 NULL；preset/agenda_index 是否与会序一致 |
| job `last_run_status=FAILED` | 查 `last_run_error`；飞书 token、LLM Key、source config 名是否存在 |
| 报告内容过简 | `MEETING_LLM_API_KEY=test` 触发 fallback；或纪要/资料为空 |
| 议程仍出现 OUTPUT 的 feishu 链 | 检查 `config_role` 是否为纯 `OUTPUT`（不应参与 merge） |
| Quartz 未触发 | `enabled=1`；`feishu.weekly-comparison.sync-polling-enabled`；日志 `Weekly comparison sync` |

---

## 8. 代码索引

| 模块 | 路径 |
|------|------|
| 核心 Facade | `matter-progress-core/.../WeeklyMatterComparisonService.java` |
| 配置仓储 | `matter-progress-core/.../JdbcMatterProgressConfigRepository.java` |
| 纪要查询 | `matter-progress-core/.../JdbcMeetingMinuteQuery.java` |
| 飞书读写 | `matter-progress-core/.../RestFeishuDocClient.java` |
| Bot 装配 | `feishu-scheduled-bot/.../MatterProgressCoreConfiguration.java` |
| Quartz 同步 | `feishu-scheduled-bot/.../WeeklyComparisonScheduleService.java` |
| 主持 WS | `meeting-server/.../MeetingHostSessionService.buildStateNode` |
| 主持 UI | `meeting-server/.../static/host-meeting.html` |
| 种子 SQL | `meeting-server/.../schema-seed/v0.9-weekly-matter-comparison-seed.sql` |
| 完整 INSERT 示例 | `meeting-server/.../schema-examples/v0.9-weekly-matter-comparison-insert.sql` |

---

## 9. 相关文档索引

| 文档 | 说明 |
| ---- | ---- |
| 本文 | 架构、DDL、config_role、Job 参数、故障排查 |
| [USER-MANUAL §1.2–1.4](USER-MANUAL.md) | meeting-server、Bot、**matter-progress-core** 分工 |
| [USER-MANUAL §3.5–3.8](USER-MANUAL.md) | 双服务部署、配置总表、**配置分层** |
| [USER-MANUAL §2.1.1 / §6.2 / §7.3](USER-MANUAL.md) | 主持页术语、会前 checklist、会序模块 |
| [feishu USER-MANUAL §12.0–12.6](../../feishu-scheduled-bot/docs/USER-MANUAL.md) | core 与 Bot 装配、YAML/DB 配置 |
| [matter-progress-core/README.md](../matter-progress-core/README.md) | core jar、`runJob`、可配置项 |

---

*文档版本：2026-05-23 · 对应 v0.9 weekly matter comparison 实现*