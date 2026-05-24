# intelligence 库 SQL 索引

## 文件职责（后续只改对应文件）

| 文件 | 职责 |
|------|------|
| [`schema-final-ddl.sql`](./schema-final-ddl.sql) | **共库新环境** 16 张业务表全量 DDL（meeting 11 + bot 5/6） |
| [`archive/`](./archive/) | 历史手工迁移，**只读**；逻辑已并入 meeting `schema.sql` / Flyway |
| [`SCHEMA-VALIDATION-20260521.md`](./SCHEMA-VALIDATION-20260521.md) | 某次生产探针对照报告（非维护入口） |

### smart-meeting-java（meeting-server）

见 [`meeting-server/src/main/resources/sql/README.md`](../meeting-server/src/main/resources/sql/README.md)

| 入口 | 路径 |
|------|------|
| meeting 全量 DDL | `meeting-server/src/main/resources/schema.sql` |
| 种子 DML | `meeting-server/src/main/resources/schema-data.sql` |
| 生产增量 | `meeting-server/src/main/resources/schema-upgrade/v*.sql` |

### feishu-scheduled-bot

| 入口 | 路径 |
|------|------|
| bot 表增量（Flyway 唯一入口） | `feishu-scheduled-bot/src/main/resources/db/migration/V*.sql` |
| 说明 | `feishu-scheduled-bot/sql/README.md` |

## 新环境推荐

**共库（meeting + bot）：**

```bash
mysql -h HOST -u USER -p intelligence < sql/schema-final-ddl.sql
mysql -h HOST -u USER -p intelligence < meeting-server/src/main/resources/schema-data.sql
mysql -h HOST -u USER -p intelligence < meeting-server/src/main/resources/schema-seed/v0.9-weekly-matter-comparison-seed.sql
```

启动 feishu-scheduled-bot 时 Flyway 会补齐 `QRTZ_*` 与版本记录；V1–V6 对已有表为幂等。

**仅 Docker Compose（meeting 单体）：** compose 挂载 `schema.sql` + `schema-data.sql`；bot 表需另执行 `schema-final-ddl.sql` 中第二节或启动 bot。

## 维护 `schema-final-ddl.sql` 时

1. **第一节（meeting）** — 与 `meeting-server/.../schema.sql` 保持一致  
2. **第二节（bot）** — 与 Flyway V1–V6 合并结果保持一致  
3. 改结构时：**先改权威入口**，再同步 `schema-final-ddl.sql`，最后（如有）加 `schema-upgrade/vX.Y-*.sql`

## 已有库升级（2026-05 前老库）

若从未跑过 v0.4+ 增量，可先对照 [`archive/schema-diff-migration-20260521.sql`](./archive/schema-diff-migration-20260521.sql)（participant 混合参会 + bot V2–V4 补齐），再按 meeting `schema-upgrade/` 版本链执行至 v0.10。
