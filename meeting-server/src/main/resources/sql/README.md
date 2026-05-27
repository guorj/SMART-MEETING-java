# meeting-server SQL 维护约定

## 唯一维护入口

| 用途 | 文件 | 何时改 |
|------|------|--------|
| **新环境建表（meeting 11 表）** | [`../schema.sql`](../schema.sql) | 表结构变更时同步改实体 + 本文件 |
| **开发/测试种子 DML** | [`../schema-data.sql`](../schema-data.sql) | 预设、默认 config 行等 |
| **已有生产库增量 DDL** | [`../schema-upgrade/`](../schema-upgrade/) | **只新增** `vX.Y-*.sql`，禁止改已发布脚本 |
| **运维种子（幂等 ON DUPLICATE）** | [`../schema-seed/`](../schema-seed/) | 功能上线配套 INSERT/UPDATE |
| **参考示例（不自动执行）** | [`../schema-examples/`](../schema-examples/) | 文档对照、手工改 URL 后执行 |
| **一次性运维/修复** | [`../sql-optional/`](../sql-optional/) | 环境相关 hotfix |

## 已有库升级顺序

按版本号依次执行（均幂等）：

```
schema-upgrade/v0.4-prod.sql
schema-upgrade/v0.5-minute.sql
schema-upgrade/v0.7-meeting-minute-content-url.sql
schema-upgrade/v0.8-meeting-preset-type-code.sql
schema-upgrade/v0.9-weekly-matter-comparison.sql
schema-upgrade/v0.10-drop-openclaw-briefing.sql
schema-upgrade/v0.14-host-agenda-docs-merge.sql   # 数据：HostAgendaV2DataMigrate --apply
schema-upgrade/v0.15-drop-matter-progress-doc-config.sql
schema-upgrade/v0.16-rename-user-mapping-feishu.sql
```

执行工具（meeting-server 目录）：

```bash
mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.ProdSchemaMigrate \
  -Dexec.classpathScope=test -Dexec.args="--apply src/main/resources/schema-upgrade/v0.10-drop-openclaw-briefing.sql"
```

## 与 feishu-scheduled-bot 共库

- meeting 表：本目录 `schema.sql` + `schema-upgrade/`
- bot 推送表：Flyway `feishu-scheduled-bot/src/main/resources/db/migration/V*.sql`（**勿**在 meeting 侧重复 ALTER bot 表）
- job 表 `int_weekly_matter_comparison_job`：已在 `schema.sql`；bot V5 为 `CREATE IF NOT EXISTS` 幂等
- **新环境整库 16 表**：仓库根 [`sql/schema-final-ddl.sql`](../../../sql/schema-final-ddl.sql)

## 测试

- H2 集成测试：[`../../test/resources/test-schema-h2.sql`](../../test/resources/test-schema-h2.sql)（结构变更时同步）
- `spring.sql.init` 生产/开发默认 **never**，勿自动跑 `schema-data.sql` 覆盖现网 URL

## 已废弃（勿再执行）

- `schema-examples/v0.5-host-agenda-briefing-example.sql` — 会中 OpenClaw 通报
- `schema-examples/v0.6-matter-progress-openclaw-briefing.sql` — ADD `openclaw_briefing` 列（v0.10 已 DROP）
- 根目录 `sql/archive/*` — 2026-05 前手工迁移，已并入 `schema.sql` / `schema-upgrade/`
