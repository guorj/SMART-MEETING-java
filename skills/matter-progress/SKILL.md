---
name: matter-progress
description: "录音页事项进度通报：读取飞书多维表格，输出Markdown进度通报正文"
allowed-tools:
  - feishu-bitable__read_bitable_rows
  - feishu-bitable__list_bitable_fields
---

# 录音页事项进度通报

## 触发条件

当 Java 后端发送 `/skill:matter-progress` 开头的消息时激活。

## 工具调用步骤

### Step 1：读取飞书多维表格

从消息中提取 `app_token`、`table_id`、`view_id`。

调用 `feishu-bitable__read_bitable_rows`：
- app_token: 消息中提供的值
- table_id: 消息中提供的值
- view_id: 消息中提供的值（可选）
- page_size: 100

若返回失败，正文开头单独一行写"未读取到飞书多维表格。"，其后可简述原因并列出推断要点。

### Step 2：生成通报

基于多维表格数据，生成"事项进度通报" Markdown 正文。

## 输出格式

**仅输出 Markdown 正文**，不要用 JSON 代码块包裹全文。

Markdown 结构建议：
- 概览（1-2段）
- 分项进度（列表或表格）
- 风险与需协调事项
- 下一步建议

## 约束

- 语言简洁专业
- 统计时间精确到小时级
- 不含数据源信息（不提及"来自飞书多维表格"）
- 不输出除通报外的闲聊
- 若无法读取多维表格，正文开头单独一行写"未读取到飞书多维表格。"