---
name: progress-analysis
description: "综合管理会会前进度通报：读取飞书多维表格与MySQL待办数据，交叉分析输出JSON"
# MCP 工具全名见 mcp-servers/LARK-MCP-TOOLS.md
allowed-tools:
  - lark-mcp__bitable_v1_appTableField_list      # bitable.v1.appTableField.list — 字段结构
  - lark-mcp__bitable_v1_appTableRecord_search   # bitable.v1.appTableRecord.search — 读表记录
  - meeting-mysql__query                         # SQL 查 int_meeting_todo 统计
---

# 上次待办进度分析

## 触发方式

Java 后端发送 `/skill:progress-analysis` 指令，附带以下业务数据：

```
/skill:progress-analysis
meetingId=meeting-002
previousMeetingId=meeting-001
previousTitle=XX项目周例会
delayed=2
inProgress=3
completed=5
delayedItems=（可选）延期项详情
```

## 执行步骤

1. **读取飞书多维表格**（lark-mcp，bot 身份）
   - 从综合管理会多维表 URL 或业务上下文解析 `app_token`、`table_id`
   - 调用 `lark-mcp__bitable_v1_appTableField_list`：path 参数 `app_token`、`table_id`，获取字段结构
   - 调用 `lark-mcp__bitable_v1_appTableRecord_search`：path 参数 `app_token`、`table_id`；`page_size` 建议 100（最大 500）；首次不传 `page_token`，若有 `has_more` 则用返回的 `page_token` 分页直至读完「📋综合管理事项代办清单」相关记录
   - 筛选与当前会议组相关的待办事项

2. **查询 MySQL 待办统计**
   - 调用 `meeting-mysql__query` 查询 `int_meeting_todo` 表：
     ```sql
     SELECT status, COUNT(*) as cnt
     FROM int_meeting_todo
     WHERE meeting_id = '{previousMeetingId}'
     GROUP BY status
     ```
   - 获取数据库侧的待办统计数据

3. **交叉分析**
   - 对比飞书多维表格与 MySQL 两个数据源的待办状态
   - 标注数据不一致项（如飞书标记"已完成"但 MySQL 仍为"进行中"）
   - 分析延期原因，归类为：资源不足、需求变更、依赖阻塞、优先级调整等

4. **输出 JSON**
   - 严格按以下格式输出，不要代码块包裹：

```json
{
  "progress_summary": "进度概述（2-3句话，包含总体完成率和关键变化）",
  "delay_reasons": [
    {"category": "资源不足", "count": 2, "detail": "前端开发人员不足，XX功能延期"}
  ],
  "high_priority_alerts": [
    {"content": "XX项目验收延期3天，需本次会议决策", "assignee": "张三"}
  ],
  "recommendations": [
    "建议本次会议优先讨论延期事项的处理方案",
    "建议增加前端开发资源支持XX项目"
  ],
  "focus_items": [
    "XX项目验收进度",
    "YY事项资源分配"
  ]
}
```

## 约束

- 若无法读取飞书多维表格（lark-mcp 异常或权限不足），在 `progress_summary` 首句标注「⚠️ 多维表数据获取失败，以下分析基于 MySQL 数据」，并仅用 MySQL 数据分析
- 若 MySQL 查询失败，在 `progress_summary` 首句标注「⚠️ 数据库查询失败，以下分析基于飞书多维表格数据」
- 两个数据源均不可用时，仅基于传入的统计数据（delayed/inProgress/completed）生成框架性分析
- `recommendations` 不超过 5 条，`focus_items` 不超过 5 条
- 输出必须是合法 JSON，不要包含注释

## Tool 名称核对

完整对照表：[mcp-servers/LARK-MCP-TOOLS.md](../mcp-servers/LARK-MCP-TOOLS.md)。若 tool 不存在，执行 `openclaw --profile clone-boss mcp list` 后更新 `allowed-tools`。
