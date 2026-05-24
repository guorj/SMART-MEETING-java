# schema-upgrade — 已有库增量 DDL

**规则：** 只新增 `vX.Y-描述.sql`，禁止修改已发布脚本。

| 文件 | 说明 |
|------|------|
| v0.4-prod.sql | 早期生产补齐 |
| v0.5-minute.sql | `int_meeting_minute` |
| v0.7-meeting-minute-content-url.sql | `content_url` |
| v0.8-meeting-preset-type-code.sql | 子表 `preset_type_code` + 回填 |
| v0.9-weekly-matter-comparison.sql | `config_role` / `generated_report_*` + job 表 |
| v0.10-drop-openclaw-briefing.sql | DROP `openclaw_briefing` |
| v0.11-bitable-display-mode.sql | `bitable_display_mode`（RAW / GROUPED） |

新环境请直接用 [`../schema.sql`](../schema.sql)，无需执行本目录。
