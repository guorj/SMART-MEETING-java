# 历史 SQL 归档（只读）

本目录脚本为 **2026-05-21 前** 手工迁移或一次性补齐，逻辑已并入：

| 归档文件 | 现维护位置 |
|----------|------------|
| `migration_20260515_hybrid_attendance.sql` | `schema.sql` → `int_meeting_participant` |
| `migration_20260510_meeting_type_preset.sql` | `schema.sql` → `int_meeting_type_preset` |
| `migration_20260510_repair_company_mojibake.sql` | 一次性 DML，无结构对应 |
| `migration_v0.1.1.sql` | 早期增量，已废弃 |
| `schema-diff-migration-20260521.sql` | participant + bot V2–V4 补齐；新库用 `schema-final-ddl.sql` |

**勿再修改本目录。** 新变更请按 [`../README.md`](../README.md) 维护对应入口。
