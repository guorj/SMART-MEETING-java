# 数据库表结构校验报告

**校验时间：** 2026-05-23（v0.10 + Flyway V6 更新）  
**数据库：** `intelligence` @ `60.205.1.17:3306`（application-dev.yml）  
**探针快照：** `meeting-server/target/prod-schema-snapshot.json`（25 张表，2026-05-21 基准）

## 范围说明

| 分类 | 表数量 | 说明 |
|------|--------|------|
| 智能会议（smart-meeting-java） | 11 | 含 v0.9 job 表；v0.10 已移除 `openclaw_briefing` |
| 飞书定时推送（feishu-scheduled-bot） | 6 | Flyway V1–V6（含 `int_scheduled_push_task_target`、log.batch_id） |
| Quartz 调度器 | 11 | `QRTZ_*`，由 feishu-scheduled-bot 创建，非业务实体 |
| 其它 | — | `flyway_schema_history` 等 |

**完整 DDL（新环境整库建表）：** [`schema-final-ddl.sql`](./schema-final-ddl.sql)  
**已有库增量迁移见：** [`archive/schema-diff-migration-20260521.sql`](./archive/schema-diff-migration-20260521.sql) + meeting `schema-upgrade/` 至 v0.10

---

## 校验结论摘要

| 表名 | 库中状态 | 项目期望 | 结论 |
|------|----------|----------|------|
| int_meeting | ✅ 一致 | schema.sql + Meeting | 对齐 |
| int_meeting_type_preset | ✅ 一致 | schema.sql + MeetingTypePreset | 对齐（注释乱码为历史编码问题，不影响结构） |
| int_meeting_participant | ⚠️ **缺 3 列** | schema.sql（已含混合参会列） | 老库见 archive/schema-diff-migration |
| int_transcript_segment | ✅ 一致 | schema.sql + TranscriptSegment | 对齐 |
| int_meeting_todo | ✅ 一致 | schema.sql + MeetingTodo | 对齐 |
| int_meeting_minute | ✅ 一致 | schema.sql | 对齐 |
| int_voiceprint | ✅ 一致 | schema.sql + Voiceprint | 对齐 |
| int_user_mapping_feishu | ✅ 一致 | schema.sql + UserMapping | 对齐 |
| int_matter_progress_doc_config | ⚠️ **v0.9 列** | schema.sql (v0.10) | 老库执行 schema-upgrade/v0.9 |
| int_weekly_matter_comparison_job | ⚠️ **新表** | v0.9 DDL + feishu V5 | bot 定时对比任务配置 |
| int_scheduled_push_log | ⚠️ **缺 V2–V4 列** | feishu V2/V3/V4 + PushLog | **需执行迁移** |
| int_scheduled_push_log_read_user | ❌ **表不存在** | feishu V2 + PushLogReadUser | **需建表** |
| int_scheduled_push_task | ✅ 一致 | feishu V1 + PushTask | 对齐 |
| int_scheduled_task_extra_date | ✅ 一致 | feishu V1 | 对齐 |
| int_scheduled_task_exclude_date | ✅ 一致 | feishu V1 | 对齐 |

---

## 智能会议模块 — 逐表对比

### int_meeting_participant（关键差异）

**项目有、库中无：**

| 列名 | 类型 | 来源 |
|------|------|------|
| attendance_mode | VARCHAR(20) NOT NULL DEFAULT 'OFFLINE' | `schema.sql`（archive: migration_20260515） |
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

## v0.9 会前事项对比通报（2026-05-23）

| 项 | 说明 |
| ---- | ---- |
| **DDL** | `schema-upgrade/v0.9-weekly-matter-comparison.sql` |
| **种子** | `schema-seed/v0.9-weekly-matter-comparison-seed.sql` |
| **Flyway** | `feishu-scheduled-bot` `V5__weekly_matter_comparison_job.sql`（仅 job 表；与 meeting DDL 勿重复建表） |
| **依赖** | `matter-progress-core` JAR；`mvn -pl matter-progress-core install` 后编译 bot |
| **文档** | [weekly-matter-comparison.md](../smart-meeting-java/docs/weekly-matter-comparison.md) |

**校验 SQL：**

```sql
SHOW COLUMNS FROM int_matter_progress_doc_config LIKE 'generated_report%';
SHOW TABLES LIKE 'int_weekly_matter_comparison_job';
```

---

## 与 schema.sql 的版本关系

| 文件 | 版本 | 角色 |
|------|------|------|
| schema.sql | v0.10 | 智能会议 11 表全量（新环境维护入口） |
| schema-upgrade/v0.5-minute.sql | v0.5 | 历史：int_meeting_minute |
| schema-upgrade/v0.9-weekly-matter-comparison.sql | v0.9 | 历史：config_role + job 表 |
| schema-upgrade/v0.10-drop-openclaw-briefing.sql | v0.10 | 历史：DROP openclaw_briefing |
| feishu-scheduled-bot/db/migration/V5 | v0.9 | job 表（共库时 meeting 侧 DDL 已建则可跳过重复） |
| feishu-scheduled-bot/db/migration/V6 | v0.10 | 多 target 子表 + push_log.batch_id |

**权威最终结构** = `schema-final-ddl.sql`（16 张业务表，合并上述全部定义）。

---

## 建议执行顺序（开发库）

```bash
# 在 meeting-server 目录，或直接用 mysql 客户端执行：
mysql -h ... -u intelligence -p intelligence < sql/archive/schema-diff-migration-20260521.sql
```

执行后可用 `ProdSchemaProbe` 重新生成快照复核。
