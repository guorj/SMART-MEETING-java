# 数据库表结构校验报告

**校验时间：** 2026-05-21  
**数据库：** `intelligence` @ `60.205.1.17:3306`（application-dev.yml）  
**探针快照：** `meeting-server/target/prod-schema-snapshot.json`（25 张表）

## 范围说明

| 分类 | 表数量 | 说明 |
|------|--------|------|
| 智能会议（smart-meeting-java） | 9 | 实体 `@TableName` + `schema.sql` |
| 飞书定时推送（feishu-scheduled-bot） | 5 | Flyway V1–V4 + JPA 实体 |
| Quartz 调度器 | 11 | `QRTZ_*`，由 feishu-scheduled-bot 创建，非业务实体 |
| 其它 | — | `flyway_schema_history` 等 |

**最终 DDL 见：** [`schema-final-ddl.sql`](./schema-final-ddl.sql)  
**补齐差异的迁移见：** [`schema-diff-migration-20260521.sql`](./schema-diff-migration-20260521.sql)

---

## 校验结论摘要

| 表名 | 库中状态 | 项目期望 | 结论 |
|------|----------|----------|------|
| int_meeting | ✅ 一致 | schema.sql + Meeting | 对齐 |
| int_meeting_type_preset | ✅ 一致 | schema.sql + MeetingTypePreset | 对齐（注释乱码为历史编码问题，不影响结构） |
| int_meeting_participant | ⚠️ **缺 3 列** | schema.sql + Participant + migration_20260515 | **需执行迁移** |
| int_transcript_segment | ✅ 一致 | schema.sql + TranscriptSegment | 对齐 |
| int_meeting_todo | ✅ 一致 | schema.sql + MeetingTodo | 对齐 |
| int_meeting_minute | ✅ 一致 | schema-upgrade-v0.5 + MeetingMinute | 对齐 |
| int_voiceprint | ✅ 一致 | schema.sql + Voiceprint | 对齐 |
| int_user_mapping | ✅ 一致 | schema.sql + UserMapping | 对齐 |
| int_matter_progress_doc_config | ✅ 一致 | schema.sql + MatterProgressDocConfig | 对齐 |
| int_scheduled_push_task | ✅ 一致 | feishu V1 + PushTask | 对齐 |
| int_scheduled_task_extra_date | ✅ 一致 | feishu V1 | 对齐 |
| int_scheduled_task_exclude_date | ✅ 一致 | feishu V1 | 对齐 |
| int_scheduled_push_log | ⚠️ **缺 V2–V4 列** | feishu V2/V3/V4 + PushLog | **需执行迁移** |
| int_scheduled_push_log_read_user | ❌ **表不存在** | feishu V2 + PushLogReadUser | **需建表** |

---

## 智能会议模块 — 逐表对比

### int_meeting_participant（关键差异）

**项目有、库中无：**

| 列名 | 类型 | 来源 |
|------|------|------|
| attendance_mode | VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' | `migration_20260515_hybrid_attendance.sql` |
| checked_in_at | DATETIME NULL | 同上 |
| check_in_source | VARCHAR(30) NULL | 同上 |

`Participant` 实体与 `MeetingHostSessionService` 混合参会（线下点名 + 线上盘点）依赖上述字段；**未迁移会导致运行时 SQL 报错**。

### 其余 8 张表

列名、类型、索引与 `meeting-server/src/main/resources/schema.sql`（v0.4 + v0.5 minute）一致。  
外键：`schema.sql` 声明了 `ON DELETE CASCADE`；库中通过 information_schema 探针未列出 FK 名，以 `SHOW CREATE TABLE` 为准（通常已存在）。

---

## 飞书定时推送模块 — 逐表对比

### int_scheduled_push_log

**库中仅有 V1 列；项目 Flyway 已定义但未落库：**

| 迁移 | 新增内容 |
|------|----------|
| V2 | feishu_message_id, read_poll_status, read_count, last_read_poll_at, read_poll_error + 表 `int_scheduled_push_log_read_user` |
| V3 | read_user.user_name |
| V4 | meeting_id, source_event_type, idempotency_key（与 smart-meeting 事件推送集成） |

### int_scheduled_push_log_read_user

整表缺失，需执行 V2+V3 DDL。

---

## 与 schema.sql 的版本关系

| 文件 | 版本 | 角色 |
|------|------|------|
| schema.sql | v0.4 | 智能会议 8 表基线（含 participant 混合参会列） |
| schema-upgrade-v0.5-minute.sql | v0.5 | int_meeting_minute（库中已存在） |
| migration_20260515_hybrid_attendance.sql | — | participant 三列（**库中未执行**） |
| feishu-scheduled-bot/db/migration/V1–V4 | — | 定时推送 5 表（**V2–V4 未完全落库**） |

**权威最终结构** = `schema-final-ddl.sql`（合并上述全部定义，以项目代码为准）。

---

## 建议执行顺序（开发库）

```bash
# 在 meeting-server 目录，或直接用 mysql 客户端执行：
mysql -h ... -u intelligence -p intelligence < sql/schema-diff-migration-20260521.sql
```

执行后可用 `ProdSchemaProbe` 重新生成快照复核。
