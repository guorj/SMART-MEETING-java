---
name: matter-progress
description: "主持会序 OpenClaw 通报：读取当前会序飞书资料，输出 Markdown 进度通报正文"
# MCP 工具全名见 mcp-servers/LARK-MCP-TOOLS.md
allowed-tools:
  - lark-mcp__bitable_v1_appTableField_list      # bitable.v1.appTableField.list — 字段结构
  - lark-mcp__bitable_v1_appTableRecord_search   # bitable.v1.appTableRecord.search — 读表记录
---

# 主持会序 OpenClaw 通报

由会议主页 **会序模块** 在会序进入 **RUNNING** 且 `openclawBriefing=true` 时触发（`AgendaBriefingService`），**非**旧版录音页手动按钮。

## 触发方式

Java 后端发送 `/skill:matter-progress` 指令，附带以下业务数据：

```
/skill:matter-progress
meetingId=meeting-002
title=XX集团综合管理会
company=XX集团
groupName=综合管理会
```

主持会序通报时可能额外附带：

```
feishuUrl=https://tenant.feishu.cn/base/xxx?table=tbl...&view=...
agendaTitle=会序2:前期项目汇报
bitableHint=【飞书资料-主持会序通报】...
```

若存在 `feishuUrl`，**必须优先**从该 URL 解析 `app_token`/`table_id`（或 docx/wiki 对应 token）并读取资料，勿改用默认综合管理表。

## 执行步骤

1. **读取飞书多维表格**（lark-mcp，bot 身份）
   - 从参数 `feishuUrl`、`bitableHint` 中的链接，或综合管理会默认多维表 URL 解析 `app_token`、`table_id`
   - 调用 `lark-mcp__bitable_v1_appTableField_list`：path 参数 `app_token`、`table_id`
   - 调用 `lark-mcp__bitable_v1_appTableRecord_search`：path 参数 `app_token`、`table_id`；`page_size` 建议 100；分页读完「📋综合管理事项代办清单」全部记录
   - 按状态分类：已完成、进行中、已延期

2. **生成 Markdown 通报正文**
   - 按以下结构输出：

```markdown
# 前期事项进度通报

## 🔴 延期事项（需重点关注）

- **[事项名称]**：负责人 XX，截止日期 XX，延期 N 天
  - 延期原因：...
  - 建议：...

## 🟡 进行中事项

- **[事项名称]**：负责人 XX，截止日期 XX，剩余 x 天
  - 当前进展：...

## 🟢 近期完成事项

- **[事项名称]**：负责人 XX，完成时间 XX

## 约束

- 若无法读取飞书多维表格（lark-mcp 异常或权限不足），首行标注「⚠️ 以下为 AI 基于上下文生成的框架性通报，多维表数据获取失败」，并基于 meetingId/title/company/groupName 上下文生成框架性内容
- 延期事项按延期天数降序排列
- 进行中事项按截止日期升序排列（截至日期近的优先展示）
- 每个分类最多展示 10 条（超出部分汇总为「等共 N 项」），延期事项全部展示；
- 不要用 JSON 代码块包裹全文，直接输出 Markdown

## Tool 名称核对

完整对照表：[mcp-servers/LARK-MCP-TOOLS.md](../mcp-servers/LARK-MCP-TOOLS.md)。若 tool 不存在，执行 `openclaw --profile clone-boss mcp list` 后更新 `allowed-tools`。
