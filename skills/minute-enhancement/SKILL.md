---
name: minute-enhancement
description: "优化会议纪要：校验初版纪要质量，补全缺失信息，标注重点待办"
allowed-tools:
  - meeting-mysql__query
---

# 会议纪要质量优化增强

## 触发条件

当 Java 后端发送 `/skill:minute-enhancement` 开头的消息时激活。

## 工具调用步骤

### Step 1：查询会议关联数据（可选）

如消息中提供了 meeting_id，可调用 `meeting-mysql__query` 获取参会人、待办等信息辅助校验：

```sql
SELECT name FROM int_participant WHERE meeting_id = '{meeting_id}'
```

### Step 2：校验并优化

对比初版纪要与转写原文片段（消息中提供），校验关键信息：

- 待办责任人是否明确
- 决议事项是否有截止日期
- 发言人归属是否准确

## 输出格式

严格输出以下 JSON：

```json
{
  "optimized_minute": "优化后的完整纪要正文",
  "quality_check": {
    "score": 85,
    "issues": ["待办责任人未明确", "决议事项缺少截止日期"]
  },
  "missing_info": ["缺失的关键信息"],
  "speaker_summary": [{"speaker": "发言人", "key_points": ["要点1"]}],
  "todo_highlight": [{"content": "待办内容", "priority": "HIGH", "assignee": "责任人"}]
}
```

## 约束

- score 为 0-100 整数，反映纪要完整度和准确性
- issues 列出具体问题，非笼统描述
- todo_highlight 的 priority 取值: HIGH / MEDIUM / LOW
- 不输出除 JSON 外的闲聊