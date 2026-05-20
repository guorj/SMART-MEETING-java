---
name: progress-analysis
description: "综合管理会会前进度通报：读取飞书多维表格与MySQL待办数据，交叉分析输出JSON"
allowed-tools:
  - feishu-bitable__read_bitable_rows
  - feishu-bitable__list_bitable_fields
  - meeting-mysql__query
---

# 上次会议待办进度深度分析

## 触发条件

当 Java 后端发送 `/skill:progress-analysis` 开头的消息时激活。

## 工具调用步骤

### Step 1：读取飞书多维表格（如 bitable 指令非空）

从消息中提取 `app_token`、`table_id`、`view_id`（由 Java 后端从配置注入）。

调用 `feishu-bitable__read_bitable_rows`：
- app_token: 消息中提供的值
- table_id: 消息中提供的值
- view_id: 消息中提供的值（可选）
- page_size: 100

若无 bitable 指令，跳过此步。

### Step 2：查询 MySQL 待办统计

调用 `meeting-mysql__query`，执行：

```sql
SELECT status, COUNT(*) AS cnt
FROM int_meeting_todo
WHERE meeting_id = '{previous_meeting_id}'
GROUP BY status
```

### Step 3：交叉分析

若 Step 1 与 Step 2 均有数据，交叉对比：
- 多维表格中的状态 vs MySQL 统计数字，标注矛盾项
- 多维表格中无但 MySQL 有的待办 → 标注"仅系统记录"
- MySQL 无但多维表格有的事项 → 标注"仅多维表格记录"

若 Step 1 失败或无数据，progress_summary 首句写"未读取到飞书多维表格"。

## 输出格式

严格输出以下 JSON，无多余文字：

```json
{
  "progress_summary": "2-3句话概述",
  "delay_reasons": [{"category": "原因分类", "count": 1, "detail": "具体说明"}],
  "high_priority_alerts": [{"content": "提醒内容", "assignee": "责任人"}],
  "recommendations": ["建议1", "建议2"],
  "focus_items": ["关注项1", "关注项2"]
}
```

## 约束

- progress_summary 必须为 2-3 句话，语言简洁
- 延期项必须有原因归类
- 若飞书多维表格不可读，progress_summary 首句必须写"未读取到飞书多维表格"
- 不输出任何除 JSON 外的闲聊或解释