---
name: minute-enhancement
description: "优化会议纪要：校验初版纪要质量，补全缺失信息，标注重点待办"
# MCP 工具全名见 mcp-servers/LARK-MCP-TOOLS.md
allowed-tools:
  - meeting-mysql__mysql_query   # 仅读库（mcp-server-mysql 只读）
---

# 纪要优化

## 触发方式

Java 后端发送 `/skill:minute-enhancement` 指令，附带业务数据：

```
/skill:minute-enhancement
meetingId=meeting-002
meetingTitle=XX集团综合管理会
meetingType=1
participants=张三,李四,王五
rawMinuteLength=3500

---

初版纪要：
（纪要正文）

转写原文片段：
（可选，用于校验）
```

## 执行步骤

1. **查询参会人信息**
   - 调用 `meeting-mysql__mysql_query` 查询参会人信息辅助校验：
     ```sql
     SELECT p.user_name, p.department, r.role
     FROM int_meeting_participant p
     JOIN int_meeting_role r ON p.id = r.participant_id
     WHERE p.meeting_id = '{meetingId}'
     ```
   - 获取参会人的部门、角色等信息，用于校验纪要中的人名引用

2. **校验初版纪要**
   - 对比初版纪要与转写原文（如有），检查遗漏的关键信息
   - 验证人名引用是否与参会人列表一致
   - 评估待办事项是否明确（责任人、截止日期）
   - 检查决议事项是否完整

3. **输出 JSON**
   - 严格按以下格式输出，不要代码块包裹：

```json
{
  "optimized_minute": "优化后的完整纪要正文（Markdown 格式，保留原有结构，补充缺失信息，修正不准确表述）",
  "quality_check": {
    "score": 85,
    "issues": [
      "待办责任人未明确",
      "决议事项缺少截止日期",
      "第三章讨论内容与转写原文不一致"
    ]
  },
  "missing_info": [
    "缺失信息1：XX决策的执行时间",
    "缺失信息2：YY事项的负责人"
  ],
  "speaker_summary": [
    {
      "speaker": "张三",
      "department": "技术部",
      "key_points": [
        "提出XX技术方案",
        "建议YY项目延期一周"
      ]
    }
  ],
  "todo_highlight": [
    {
      "content": "完成XX项目验收",
      "priority": "HIGH",
      "assignee": "张三",
      "operator": "李四",
      "deadline": "2026-05-25"
    }
  ]
}
```

## 约束

- 若 MySQL 查询失败，跳过参会人校验步骤，在 `quality_check.issues` 中标注「⚠️ 参会人信息查询失败，未进行人名校验」
- `optimized_minute` 保留原版纪要的整体结构和主要结论，仅做补充和修正，不重写
- `score` 为 0-100 的整数，基于以下标准：
  - 待办事项完整性（30分）：每项待办有明确责任人和截止日期
  - 决议记录完整性（25分）：关键决策有明确结论
  - 人名引用准确性（20分）：所有引用人名与参会人一致
  - 内容覆盖度（15分）：与转写原文对比无重大遗漏
  - 格式规范性（10分）：结构清晰、层次分明
- `priority` 可选值：HIGH / MEDIUM / LOW
- 输出必须是合法 JSON，不要包含注释
