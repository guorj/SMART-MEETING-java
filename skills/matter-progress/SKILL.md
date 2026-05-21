---
name: matter-progress
description: "录音页事项进度通报：读取飞书多维表格，输出Markdown进度通报正文"
allowed-tools:
  - feishu-bitable__read_bitable_rows
  - feishu-bitable__list_bitable_fields
---

# 事项进度通报

## 触发方式

Java 后端发送 `/skill:matter-progress` 指令，附带以下业务数据：

```
/skill:matter-progress
meetingId=meeting-002
title=XX集团综合管理会
company=XX集团
groupName=综合管理会
```

## 执行步骤

1. **读取飞书多维表格**
   - 调用 `feishu-bitable__list_bitable_fields` 获取字段结构
   - 调用 `feishu-bitable__read_bitable_rows` 读取「📋综合管理事项代办清单」全部记录
   - 按状态分类：已完成、进行中、已延期、未开始

2. **生成 Markdown 通报正文**
   - 按以下结构输出：

```markdown
# 事项进度通报

> 📊 数据来源：飞书多维表格「📋综合管理事项代办清单」
> 🕐 更新时间：{当前时间}

## 📈 总体概览

| 状态 | 数量 | 占比 |
|------|------|------|
| ✅ 已完成 | N | XX% |
| 🔄 进行中 | N | XX% |
| ⚠️ 已延期 | N | XX% |
| ⏳ 未开始 | N | XX% |

## 🔴 延期事项（需重点关注）

- **[事项名称]**：负责人 XX，截止日期 XX，延期 N 天
  - 延期原因：...
  - 建议：...

## 🟡 进行中事项

- **[事项名称]**：负责人 XX，进度 XX%，预计完成 XX
  - 当前进展：...

## 🟢 近期完成事项

- **[事项名称]**：负责人 XX，完成时间 XX

## ⚡ 风险与需协调事项

1. ...
2. ...

## 💡 下一步建议

1. 优先处理延期事项，建议本次会议讨论...
2. ...
```

## 约束

- 若无法读取飞书多维表格（MCP Server 异常），首行标注「⚠️ 以下为 AI 基于上下文生成的框架性通报，多维表数据获取失败」，并基于 meetingId/title/company/groupName 上下文生成框架性内容
- 延期事项按延期天数降序排列
- 进行中事项按进度百分比升序排列（进度低的优先展示）
- 每个分类最多展示 10 条，超出部分汇总为「等共 N 项」
- 不要用 JSON 代码块包裹全文，直接输出 Markdown
