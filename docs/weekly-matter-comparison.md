# 会前事项对比通报（Weekly Matter Comparison）

> 调度：`feishu-scheduled-bot` · 业务：`matter-progress-core` · 展示：`meeting-server` 主持页

## 概述

定时（或手动）将 preset 会序 **SOURCE** 的 oabp 事项表与近 N 天 **READY** 纪要交叉对比，产出一批 **结构化事项**（每条入库一行），写回 preset `host_agenda` OUTPUT 行的 `generatedReportRunId`，主持页按三组展示。

- **不发**飞书群消息
- **不覆盖** SOURCE/OUTPUT 的 `feishuDocUrl`（主持页资料外链仍可保留）
- **SOURCE 数据**：父会序项 `host_agenda.items[].oabpTaskSql`（只读 SELECT → oabp 库）
- **纪要数据**：`intelligence.int_meeting_minute`
- **产物**：`intelligence.int_weekly_matter_comparison_run` + `int_weekly_matter_comparison_item`（v0.26 起不再写飞书 Doc）

## 架构

```text
Quartz / POST …/execute
    → WeeklyMatterComparisonService.runJob(id)
        → MCP（默认）：WS 下发 oabp SQL → Agent meeting-mysql 查 SOURCE + 纪要 → 返回 items JSON 块
        → Legacy（fallback）：Java OabpSourceQuery + LLM Markdown → 解析为 items
    → INSERT run (1) + INSERT items (N)
    → UPDATE host_agenda.generatedReportRunId + job.last_run_id
    → meeting-server 主持页按 run_id 查 items 展示三组
```

## 库表（v0.26）

| 表 | 粒度 | 说明 |
|----|------|------|
| `int_weekly_matter_comparison_run` | 每次 job 1 行 | 批次头：title/status/item_count/generated_at |
| `int_weekly_matter_comparison_item` | 每行 1 事项 | category/matter_name/assignee/time_node/status_label |
| `int_weekly_matter_comparison_job` | job 定义 | 新增 `last_run_id` 指向最新 run |

`assignee` 缺失统一存 NULL（前端显示「未提及」）。`generation_status`：READY/PARTIAL/FAILED。

## 配置分层

| 层级 | 位置 | 说明 |
|------|------|------|
| 任务 | `int_weekly_matter_comparison_job` | cron、`source_config_names`、纪要查询、OUTPUT config |
| SOURCE | preset `host_agenda` docs[] + **item.oabpTaskSql** | `configName` + role=SOURCE/BOTH；**必须**配置 oabpTaskSql |
| OUTPUT | preset `host_agenda` docs[] role=OUTPUT | 写回 `generatedReportRunId` |
| Bot | `feishu.weekly-comparison.*` | OpenClaw、LLM、oabp 连接 |

未配置 `oabpTaskSql` → 任务 **fail-fast**。

## 执行路径

| 条件 | 路径 | SOURCE | 产出 |
|------|------|--------|------|
| `delegate-to-mcp=true` 且 MCP 成功 | MCP | Agent `meeting-mysql` 执行 SQL | Agent 返回 items JSON → Bot 入库 |
| MCP 失败且 `legacy-fallback-on-mcp-failure=true` | Legacy | Bot `OabpSourceQuery` 直连 oabp | LLM Markdown → 解析 items → 入库 |
| `delegate-to-mcp=false` | Legacy | 同上 | 同上 |

## Agent 产出协议

```
-----BEGIN_WEEKLY_COMPARISON_ITEMS-----
{ "generatedAt":"...","items":[{"category":"DELAYED","matterName":"...","assignee":"...","timeNode":"...","statusLabel":"延期","sortOrder":1,"sourceConfigName":"..."}] }
-----END_WEEKLY_COMPARISON_ITEMS-----
```

`category` 枚举 `DELAYED`/`COMPLETED`/`IN_PROGRESS`，须与 `statusLabel` 一致；`matterName` 必填；缺失字段省略（不写「未提及」字符串）。

## 运维

1. 管理后台为各会序配置 **oabp 项目任务 SQL**（见 [开关手册.md](开关手册.md)）
2. 执行 `schema-upgrade/v0.26-weekly-matter-comparison-report.sql`
3. `mvn -pl meeting-config-core,matter-progress-core,meeting-server install` 后重启 feishu-scheduled-bot
4. 试跑：`POST /api/weekly-comparison/jobs/{id}/execute`

## 相关文档

- [weekly-matter-comparison-USER-MANUAL.md](weekly-matter-comparison-USER-MANUAL.md) — 使用手册
- [matter-progress-core/README.md](../matter-progress-core/README.md)
- [feishu-scheduled-bot USER-MANUAL §12](../../feishu-scheduled-bot/docs/USER-MANUAL.md)
- [开关手册.md](开关手册.md) — oabp 表结构与 SQL 示例
- [WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md](../mcp-servers/WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md)
