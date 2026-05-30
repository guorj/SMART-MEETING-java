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
| `api-reference` | **数据更新 API** 目录：方法/路径/请求响应示例/cURL（`GET /api/v1/admin/api-reference/catalog`） |
| `presets` | 会务预设 `int_meeting_type_preset`（会序+资料一体：`host_agenda` v2 `items[].docs[]`） |
| `weekly-jobs` | `int_weekly_matter_comparison_job` CRUD；**立即执行**、**同步 Quartz**（经 bot API） |
| `push-bot` | feishu-scheduled-bot **推送任务 / 日志**（经 `BotBridgeService` 代理 `/api/tasks`、`/api/logs`） |
| `users` | **用户管理**：按 userId 一行聚合 `int_user_mapping_feishu`（仅 `feishu_user_id`）+ `int_voiceprint`；`GET/PUT /api/v1/admin/users` |
| `meetings` | 会议列表/详情/主持与录音链接；详情可跳转 **会议推送日志** |
| `pipeline` | **流水线编排**：模板/步骤/执行记录管理；支持条件分支、回调等待（`WAITING_CALLBACK`）、PRE/POST 自动触发 |
| `settings` | `int_meeting_system_config` + 通知 meeting-server 热加载 |

> meeting-server 飞书指令体系已切换为统一入口：用户发送 `会议管理`，机器人返回 dashboard 入口卡片；开始会议、声纹注册、纪要查看均在 `dashboard.html` 内完成。

## 系统参数热加载

1. Admin 写入 `int_meeting_system_config`
2. 回调 `POST {MEETING_SERVER_URL}/api/v1/internal/runtime-config/reload`，头 `X-Internal-Token`
3. meeting-server `MeetingRuntimeConfigLoader` 覆盖主持检点等开关

环境变量（两进程需一致）：

- `INTERNAL_RELOAD_TOKEN`（默认 `dev-internal-reload`）
- `ADMIN_TOKEN` / `MEETING_SERVER_URL`
- `MEETING_NOTIFY_BOT_URL` / `SCHEDULED_BOT_APIKEY`（默认 `jq_int_meeting_key`；三进程须相同）
- 生产反代示例：`https://oa.qdyhjz.cn/scheduled-bot`（Nginx **不剥**前缀；bot `prod` 配置 `server.servlet.context-path=/scheduled-bot`，与 meeting-server `/meeting-server` 同理）

> **重要（2026-05 更新）**  
> `MEETING_SERVER_URL` 必须带 meeting-server 上下文前缀，推荐直接配置：  
> `MEETING_SERVER_URL=http://127.0.0.1:8765/meeting-server`  
> 否则 admin 桥接调用 pipeline internal（如 `POST /api/v1/internal/pipeline/execute-by-preset`）可能出现 `404 Not Found`。

## Bot 桥接（推送调度）

`meeting-admin-server` 通过 `BotBridgeService` 持有 `X-API-Key`，浏览器仅使用 `X-Admin-Token`：

| Admin API | Bot API |
|-----------|---------|
| `GET/POST/PUT/DELETE /api/v1/admin/push-tasks/*` | `/api/tasks/*` |
| `GET /api/v1/admin/push-logs` | `/api/logs`（含 `meetingId` 筛选） |
| `POST /api/v1/admin/bot/reload-schedule` | `/api/admin/reload-schedule` |
| `POST /api/v1/admin/weekly-jobs/{id}/execute` | `/api/weekly-comparison/jobs/{id}/execute` |
| `GET /api/v1/admin/integrations/health` | bot 可达性探测 |

UI：`/admin#/push-bot`（任务 + 日志 Tab）；`/admin#/weekly-jobs`（对比任务 + 立即执行）。

### 推送任务「调度模式」说明（UI 字段注释）

| scheduleMode | 效果 |
|--------------|------|
| **EXTERNAL** | **不注册** Quartz Cron，仅由 **POST /api/tasks/{id}/execute** 或管理台「执行」触发。Cron 可留空。 |
| **INTERNAL** | feishu-scheduled-bot 内 **Quartz 按 Cron 自动推送**。管理台「执行」在 `both` 模式且 `allow-internal-manual-execute=false` 时可能 **409 REJECTED**。 |

**手动「执行」/ `/execute`**：不做当日 DUPLICATE 去重，每次都会尝试真实推送（假日/excludeDates 仍生效）。需 **重新编译并重启 feishu-scheduled-bot** 后生效。

全局 `feishu.schedule.mode`（`internal` / `external` / `both`）在 bot 侧控制两种任务是否并存及 `/execute` 对 INTERNAL 的约束；任务级 `scheduleMode` 决定是否注册 Cron。

前端字段说明集中维护于 `static/admin/admin-form-hints.js`，各模块表单引用 `AdminHints.*`。

## DDL

执行 `meeting-server/src/main/resources/schema-upgrade/v0.12-meeting-system-config.sql`（幂等）。

### 二期流水线/事件驱动升级（v0.17）

执行 `meeting-server/src/main/resources/schema-upgrade/v0.17-pipeline-outbox-statemachine.sql`（幂等），新增：

- `int_event_outbox`：事务外盒事件表（可靠投递）
- `int_pipeline_template` / `int_pipeline_step`：pipeline 模板与步骤配置
- `int_pipeline_step_execution`：pipeline 运行时执行记录（含 timeout/retry）
- `int_processed_command`：命令幂等处理记录

管理端新增 API（`/api/v1/admin/pipeline/*`）与页面（`/admin#/pipeline`）用于模板、步骤和执行触发管理。

### 流水线增强（2026-05）

当前版本已在 v0.17 基础上补齐以下能力（无需新增表结构）：

- 执行态：新增 `WAITING_CALLBACK`（用于飞书卡片回调后续跑）
- 分支能力：`configJson.condition` 条件表达式（meeting/context 字段匹配）
- 上下文透传：`sharedContextJson`（跨步骤状态传递）
- 调度触发：
  - `PRE`：支持会前 **24h / 10min** 双时点自动触发（可分别绑定模板）
  - `POST`：会议结束后自动触发
  - `MID`：当前按业务要求保持手动/外部触发（暂不加自动触发器）

推荐在 `application*.yml` 或环境变量配置：

- `meeting.scheduler.pre-enabled`
- `meeting.scheduler.pre-window-minutes`
- `meeting.scheduler.pre-24h-template-code`
- `meeting.scheduler.pre-10m-template-code`
- `meeting.pipeline.post-auto-trigger.enabled`
- `meeting.pipeline.post-auto-trigger.template-code`

### 三场景音频链路升级（v0.18）

执行 `meeting-server/src/main/resources/schema-upgrade/v0.18-meeting-scenario-audio-fallback.sql`（幂等），新增：

- `int_meeting.meeting_scenario`：会议场景（`OFFLINE|HYBRID|ONLINE`）
- `int_meeting.source_audio_url`：云端录音文件 URL（混合/纯线上兜底）

落地行为：

- 创建会议时若未显式指定场景，服务端按参会人 `attendanceMode` 自动推导：
  - 全 `OFFLINE` → `OFFLINE`
  - 全 `ONLINE` → `ONLINE`
  - 混合 → `HYBRID`
- 会后纪要链路在没有可用本地音频时，允许下载 `source_audio_url` 到本地缓存目录，再复用既有离线 ASR + 纪要生成流程。

## P1 能力（当前）

| 模块 | 新增能力 |
|------|----------|
| **数据查看** | 按 meetingId 查纪要、转写片段 |
| **会议运维** | 状态/preset 筛选、参会人列表、`POST .../force-end` |
| **对比任务** | 任务编辑表单、matter config 名下拉 |
| **会序与资料** | 会序行内嵌资料绑定，一次保存（`agenda-bundle`） |
| **系统参数** | 按 category 分组、恢复默认、变更审计 `v0.13` |

DDL：除 `v0.12` 外，执行 `v0.13-meeting-system-config-audit.sql`（新库已写入 `schema.sql` 尾部）。

## 数据更新 API 参考

- UI：`/admin#/api-reference`
- 元数据：`GET /api/v1/admin/api-reference/meta`
- 目录：`GET /api/v1/admin/api-reference/catalog`（源码维护于 `AdminDataApiCatalogService`）    
- 覆盖：agenda-config、meetings、system-config、weekly-jobs、meeting-server internal 桥接接口

## P1.5 / 继续迭代（当前）

| 能力 | 说明 |
|------|------|
| 会序与资料一体化 | `GET/PUT .../agenda-bundle`：每行含 `bindings[]`，保存时按行序重写 `agenda_index` 并清理孤儿 doc |
| 会序表（兼容） | `GET/PUT .../agenda-items` 仅 host_agenda |
| 会序校验 | `POST .../validate-agenda`（检点关键词提示等） |
| 资料卡片 | 挂在会序行下；`PUT …/agenda-bundle` 写入 `host_agenda` JSON（已移除独立 `doc-bindings` API） |
| JSON 高级 | 仅 `host_agenda_json`，不联动资料索引 |
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

### Pipeline 管理员操作建议（最新）

1. 在 `#/pipeline` 创建两套 PRE 模板：`pre_24h_default`、`pre_10m_default`。  
2. 在服务配置中分别绑定 24h / 10min 模板编码。  
3. POST 模板建议保持单套默认模板，开启自动触发。  
4. 会中 MID 步骤按当前策略继续手动触发，避免误触发影响会中节奏。  
5. 执行记录出现 `WAITING_CALLBACK` 时，先检查飞书卡片回调地址、token 与按钮点击链路。  
6. 若需会前收集会序资料（URL/分钟数），可使用 `pre-agenda-fill-init` + `pre-agenda-fill-notify`，回填页面入口为 `/meeting-server/agenda-fill.html?token=...`。  

### 会前流程编排（零基础操作）

如果你希望由会务同学直接在后台搭建会前流程（不写代码），请直接使用：

- [会前流程编排指南-零基础.md](会前流程编排指南-零基础.md)

该指南覆盖：

- 会前 24h / 10min 双模板搭建
- 步骤顺序与可复制的 `configJson`
- `WAITING_CALLBACK`、`FAILED`、`TIMEOUT` 排障
- 上线前检查清单（非技术版本）
- 会序资料回填链路（个人/群/混合通知）配置样例

详见 [meeting-admin-p2.md](meeting-admin-p2.md)。

## 扩展

见 [admin-extensibility.md](admin-extensibility.md)。
