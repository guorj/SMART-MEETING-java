---
name: matter-progress
description: "主持会序 OpenClaw 通报：对照上次会议纪要 + 飞书多维表，输出三部分 Markdown 进度通报"
# MCP 工具全名见 mcp-servers/LARK-MCP-TOOLS.md
allowed-tools:
  - lark-mcp__bitable_v1_appTableField_list
  - lark-mcp__bitable_v1_appTableRecord_search
  - meeting-mysql__mysql_query
  - lark-mcp__docx_v1_document_create
  - lark-mcp__docx_v1_documentBlockChildren_create
  - lark-mcp__docx_v1_document_rawContent
  - lark-mcp__wiki_v2_space_getNode
---

# 主持会序 OpenClaw 通报

由会议主页 **会序模块** 在会序进入 **RUNNING** 且 `openclawBriefing=true` 时触发（`AgendaBriefingService`），**非**旧版录音页手动按钮。

## 触发方式

Java 后端发送 `/skill:matter-progress` 指令，附带以下业务数据：

```
/skill:matter-progress
title=XX集团综合管理会
company=XX集团
groupName=综合管理会
```

- 若存在 `feishuUrl`，**必须优先**从该 URL 解析 `app_token`/`table_id` 并读取资料，勿改用默认综合管理表。
- 若未传 `previousMeetingId`，用 `meeting-mysql__mysql_query` 查当前会议的 `previous_meeting_id` 再读纪要。

## 执行步骤

### 1. 读取上次会议纪要（meeting-mysql）

```sql
SELECT m.meeting_id, im.title, m.content_markdown, m.content_url, m.generation_status, m.generated_at
FROM int_meeting_minute m
LEFT JOIN int_meeting im ON im.id = m.meeting_id
WHERE m.meeting_id = '{previousMeetingId}'
  AND m.generation_status = 'READY'
ORDER BY m.generated_at DESC
LIMIT 1
```

- `content_markdown` 为空时尝试 `content_url`（飞书 Doc）；仍读不到则在第一部分注明「上次纪要不可用」。
- 从纪要正文中提取**待办/决议/跟进事项**清单（事项名称、责任人、计划节点）；每条记为「纪要事项」。

### 2. 读取飞书多维表格（lark-mcp）

- 调用 `lark-mcp__bitable_v1_appTableField_list`：了解列名与类型。
- 调用 `lark-mcp__bitable_v1_appTableRecord_search`：`page_size` 建议 100，分页读完全部记录。
- 将记录映射为：事项名称、负责人、状态、截止日期、完成日期、进展说明等。

**日期字段识别**（按用途区分，勿混用）：


| 用途     | 字段名特征（按优先级取第一个可用）                                             |
| ------ | ------------------------------------------------------------- |
| 实际完成日期 | ① 含「完成」+「时间/日期」 ② 含「关闭」+「时间/日期」 ③ 含「结束」+「时间/日期」 ④ 终态记录取最后修改时间 |
| 计划截止   | 含「截止/计划完成/预计完成/deadline」                                      |
| ⚠️ 禁止  | 用计划截止字段充当实际完成日期                                               |


**状态判定**（以多维表状态字段为主，日期为辅）：

- **已完成**：状态为「已完成/已关闭/已完成 ✅」等终态。
- **延期**：状态含「延期」；或状态非终态且计划截止日 < 当日。
- **进行中**：状态为进行中/未开始等，且未满足延期条件。

### 3. 纪要与多维表对齐

- **匹配**：纪要事项 ↔ 多维表记录，按事项名称、责任人、关键词（≥2 字）模糊匹配；一条纪要事项最多对应一条表记录。
- **纪要内**：成功匹配的多维表记录 → 归入**第一部分**对应子类。
- **纪要外**：多维表中**未**与任何纪要事项匹配、且判定为**延期**的记录 → 归入**第二部分**。
- 无法匹配且非延期的表记录**不输出**（除非第三部分风险提示需要引用）。

### 4. 生成 Markdown 通报正文

**只输出以下三部分**，不要前言、后记、读表过程、字段说明、空章节标题：

```markdown
# 前期事项进度通报

## 一、上次会议纪要事项跟进

> 基准纪要：{previousTitle 或 previousMeetingId}；跟进区间：**{上周周一 YYYY-MM-DD} 至 {当日 YYYY-MM-DD}**（含边界）

### 已完成

- **[事项名称]**：负责人 XX，完成时间 XX

### 延期

- **[事项名称]**：负责人 XX，截止 XX，延期 N 天

### 进行中

- **[事项名称]**：负责人 XX，截止 XX，剩余 N 天

## 二、纪要外延期事项

- **[事项名称]**：负责人 XX，截止 XX，延期 N 天

## 三、风险提示

- （每条一行：需决策/资源/依赖/重大延期等，附事项名与负责人）
```

## 跟进区间与完成日期

- **跟进区间** = **[上周周一, 当日]**（含边界）；「上周周一」= 当日所在周的上一周的周一。
  - 例：当日 5 月 24 日（周日）→ 上周周一 5 月 11 日，区间 5/11–5/24。
  - 例：当日 5 月 20 日（周三）→ 上周周一 5 月 12 日，区间 5/12–5/20。
- **基准纪要行**：输出 `基准纪要：{标题或 meeting_id}；跟进区间：{上周周一} 至 {当日}`，**不得**仅用 `generated_at` 代替跟进区间。
- **「已完成」子类**：仅列纪要内事项且实际完成日期 ∈ 跟进区间；按完成日降序。
- **完成日期为空**的终态记录：在「已完成」末行写「另有 N 项已完成但完成时间不详」，不逐条展开。
- **「延期」「进行中」**：不限完成日期区间，但仍须为纪要内且已匹配多维表的事项。

## 约束

- **结构**：正文仅含「一、二、三」三部分；无数据的小节写「无」一行，不要删除小节标题。
- **第一部分**：只列**纪要中提取到**且已在多维表匹配的事项；按子类内：延期按延期天数降序，进行中按截止日升序，已完成按完成日降序。
- **第二部分**：只列**未出现在纪要中**且**已延期**的多维表事项；全部展示，按延期天数降序。
- **第三部分**：3–5 条为宜；聚焦需会上关注的风险（重大延期、多次延期、纪要承诺未落地、截止 3 日内未完成等）；无风险写「无」。
- **篇幅**：每子类最多 10 条，超出写「等共 N 项」；第二部分延期项不截断。
- 不要使用数据缓存；若多维表读取失败，首行写「⚠️ 多维表数据获取失败」，第一部分仅基于纪要文字归纳，第二、三部分写「无」或据纪要推断标注「待核实」。
- 若纪要读取失败且无 `previousMeetingId`，第一部分写「⚠️ 上次纪要不可用」，第二、三部分仅基于多维表（第二部分仍只列延期）。
- 不要用 JSON 代码块包裹全文；不要输出读表步骤、工具名、SQL、筛选规则说明。

## 硬性约束（禁止违反）

- **禁止**复用历史轮次、会话记忆或「已有缓存」跳过读表/读库；每次 `/skill:matter-progress` 必须重新调用 lark-mcp 与 meeting-mysql。
- **禁止**在正文中说明「直接使用缓存」「已有缓存数据」等。
- **禁止**输出纪要增强 JSON（`optimized_minute`、`quality_check`、`missing_info` 等）；仅输出上述 Markdown 通报。
- **禁止**输出第四部分及「本周完成」「进行中事项（全表）」等旧版结构；**禁止**寒暄、总结段、附录、数据来源说明。
- 若 `requestId` 与上一轮不同，视为新任务，必须重新读表并生成新通报。

## weekly-comparison-mcp 模式（会前对比通报）

当 prompt 含 **`mode=weekly-comparison-mcp`** 时，**覆盖**上文「只输出 Markdown 三部分」规则，改按 Bot 下发的 **执行手册** 完成全链路：

1. 读 SOURCE（bitable / docx / wiki MCP）
2. 读纪要（`meeting-mysql__mysql_query` + `[minute_query_sql]`）
3. 内存中交叉对比，生成**完整对比报告 Markdown**（分组与排序以执行手册为准，摘要如下）：
   - **分组顺序**：`## 延期事项` → `## 已完成事项` → `## 进行中事项`
   - **互斥归类**：每条仅入一组；`状态=已完成` 不得入延期/进行中；未逾期不得入延期
   - **组内排序**：已完成按完成时间（无则截止时间）**降序**；延期按逾期程度从重到轻
4. **MCP 写飞书 Doc**（必须）：
   - `lark-mcp__docx_v1_document_create`（`title` = `outputDocTitle`，可选 `folder_token`）
   - `lark-mcp__docx_v1_documentBlockChildren_create`：根 **`block_id` = `document_id`**；Markdown 按行转 `block_type=2` 文本 block；**每批 ≤50 个 children**；禁止逐行单独 MCP
5. 回复末行（必填）：`generatedReportUrl=<完整 docx URL>`（Bot JDBC 写回 `generated_report_url`）

**主持会序通报**（无 `mode=weekly-comparison-mcp`）：仍只输出上文三部分 Markdown，**不**调用 docx 写工具。

## Tool 名称核对

完整对照表：[mcp-servers/LARK-MCP-TOOLS.md](../mcp-servers/LARK-MCP-TOOLS.md)。若 tool 不存在，执行 `openclaw --profile clone-boss mcp list` 后更新 `allowed-tools`。



