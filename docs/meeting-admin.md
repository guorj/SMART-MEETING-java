# 会议管理后台（meeting-admin-server）

独立 Spring Boot 进程，与 **meeting-server**（会中 :8765）、**feishu-scheduled-bot**（:8764）三进程分离部署，共享 MySQL `intelligence` 库。

## 端口与启动

| 进程 | 模块 | 默认端口 |
|------|------|----------|
| 管理后台 | `meeting-admin-server` | **8766** |
| 会中服务 | `meeting-server` | **8765** |
| 定时对比 | `feishu-scheduled-bot` | **8764** |

**一键启动**（[`scripts/start-dev.sh`](../scripts/start-dev.sh)）：

```bash
cd smart-meeting-java
chmod +x scripts/start-dev.sh
./scripts/start-dev.sh          # 菜单：1=server 2=admin 3=both
./scripts/start-dev.sh server
./scripts/start-dev.sh admin
./scripts/start-dev.sh both --local-deps
```

自动：Java/Maven 检查、MySQL/Redis 端口探测、`mvn compile`、`spring-boot:run`（profile=`dev`）。

**手动启动**：

```bash
cd smart-meeting-java
./mvnw spring-boot:run -pl meeting-server
./mvnw spring-boot:run -pl meeting-admin-server
```

## 访问

- 后台 UI：`http://127.0.0.1:8766/admin`
- 请求头：`X-Admin-Token`（默认 dev：`dev-admin-token`，见 `application-dev.yml`）
- meeting-server **无** `/admin` 路由

## 模块

| 模块 ID | 说明 |
|---------|------|
| `presets` | 会务预设 `int_meeting_type_preset`、资料 `int_matter_progress_doc_config` |
| `weekly-jobs` | `int_weekly_matter_comparison_job` CRUD |
| `meetings` | 会议列表/详情/主持与录音链接 |
| `settings` | `int_meeting_system_config` + 通知 meeting-server 热加载 |

## 系统参数热加载

1. Admin 写入 `int_meeting_system_config`
2. 回调 `POST {MEETING_SERVER_URL}/api/v1/internal/runtime-config/reload`，头 `X-Internal-Token`
3. meeting-server `MeetingRuntimeConfigLoader` 覆盖主持检点等开关

环境变量（两进程需一致）：

- `INTERNAL_RELOAD_TOKEN`（默认 `dev-internal-reload`）
- `ADMIN_TOKEN` / `MEETING_SERVER_URL`

## DDL

执行 `meeting-server/src/main/resources/schema-upgrade/v0.12-meeting-system-config.sql`（幂等）。

## P1 能力（当前）

| 模块 | 新增能力 |
|------|----------|
| **数据查看** | 按 meetingId 查纪要、转写片段 |
| **会议运维** | 状态/preset 筛选、参会人列表、`POST .../force-end` |
| **对比任务** | 任务编辑表单、matter config 名下拉 |
| **会序与资料** | doc 绑定增删改（prompt 表单） |
| **系统参数** | 按 category 分组、恢复默认、变更审计 `v0.13` |

DDL：除 `v0.12` 外，执行 `v0.13-meeting-system-config-audit.sql`（新库已写入 `schema.sql` 尾部）。

## P1.5 / 继续迭代（当前）

| 能力 | 说明 |
|------|------|
| 会序表编辑 | `GET/PUT .../agenda-items`，表格模式 + JSON 高级模式 |
| 会序校验 | `POST .../validate-agenda`（检点关键词提示等） |
| 资料绑定抽屉 | 侧栏表单替代 prompt |
| 批量刷新快照 | `POST .../refresh-meetings-host-agenda?dryRun=`，仅 ISSUE_COLLECTING/INVITED |
| file_based Provider | `meeting.admin.agenda-config.file-enabled=true` 时只读加载 classpath JSON |
| 对比任务校验 | 保存前校验 cron、JSON、必填项 |

## P2 能力

| 能力 | 说明 |
|------|------|
| Internal 批量刷新 | 经 meeting-server `syncHostAgendaForCreate` **含飞书 enrich** |
| preset 缓存刷新 | 保存 preset 后自动刷 Redis；可手动触发 |
| 飞书 OAuth | 可选替代静态 Token（默认关） |
| 集成入口 | bot / meeting-server 外链与反代说明 |

详见 [meeting-admin-p2.md](meeting-admin-p2.md)。

## 扩展

见 [admin-extensibility.md](admin-extensibility.md)。
