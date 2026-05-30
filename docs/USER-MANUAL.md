# 智能会议系统 — 用户手册

**文档版本：** 2026-05-29 · 与当前代码实现对齐（含 Pipeline 二期编排增强）

---

## 目录

1. [产品简介](#1-产品简介)
2. [三场景会议音频策略（v0.18）](#2-三场景会议音频策略v018)
3. [系统使用指南（实践篇）](#3-系统使用指南实践篇)
4. [角色与职责](#4-角色与职责)
5. [常见问题与故障排查](#5-常见问题与故障排查)
6. [附录](#6-附录)

---

## 1. 产品简介

**智能会议系统**以飞书群聊/单聊为入口，提供会议创建、现场录音、AI 主持、混合检点、LLM 纪要生成、会前事项对比通报等能力。

### 1.1 一场会的完整时间线

```
[会务] 飞书「会议管理」→ 打开统一会议前台（dashboard）→ 选类型创建会议
    ↓
[会前] Bot 定时生成「事项对比通报」→ 写入数据库 → 主持页自动展示链接
    ↓
[操作员/主持] 打开会议主页 → 点击「开始会议」→ 启动录音 + AI 主持
    ↓
[检点] 线上个人链接确认到会 → 线下点名答到
    ↓
[主持] 下一议题/加时/跳过 → 会序模块展示资料 + 对比通报链接
    ↓
[操作员] 点击「结束会议」→ 纪要生成 → 飞书推送文档链接
```

### 1.2 三个服务与分工

| 服务 | 端口 | 职责 | 不负责 |
|------|------|------|--------|
| **meeting-server** | 8765 | 会议全生命周期：建会、录音、主持、纪要 | 定时推送、会前对比写库 |
| **feishu-scheduled-bot** | 8764 | 定时飞书推送、会前对比通报生成 | 会中录音/主持 |
| **matter-progress-core** | (jar) | 对比流水线核心逻辑 | 无独立进程、不发 IM |

### 1.3 当前能力边界

| 能力 | 状态 | 说明 |
|------|------|------|
| 飞书统一入口工作台 | 可用 | 发送「会议管理」进入 dashboard，内含开始会议/注册声纹/查看纪要 |
| 混合线上线下检点 | 可用 | 线上个人链接 + 线下点名 |
| AI 主持 TTS、议题切换 | 可用 | 支持议题超时策略（仅提醒 / 自动下一议题 / 等待主持人决策） |
| 现场录音 + 实时 ASR | 可用 | 可关闭实时转写走离线 ASR |
| 会后纪要 + 待办提取 | 可用 | LLM 生成 + AI 增强（可选） |
| 会前事项对比通报 | 可用 | Bot 定时生成 + OpenClaw 主路径 |
| Pipeline 会务编排（PRE/MID/POST） | 可用 | 支持步骤模板、条件分支、等待卡片回调、跨步骤上下文 |
| 建会待办进度卡片 | 已下线 (v0.9) | 由会前事项对比通报取代 |
| 三场景会议支持 (v0.18) | 可用 | OFFLINE/HYBRID/ONLINE 自动推导 + 云端录音 URL 兜底下载（纯线上/混合场景） |

### 1.4 当前项目能力总览（2026-05）

| 阶段 | 已上线能力 | 当前实现状态 |
|------|------------|--------------|
| 会前（PRE） | 议题归集通知、参会确认卡片、会前盘点卡、上次纪要链接附带、声纹就绪检查 | 已接入 Pipeline 编排；支持条件分支与回调后续跑 |
| 会中（MID） | AI 主持、议题计时提醒、议题超时策略、上次待办进度通报 | 可用；超时策略支持仅提醒/自动下一议题/等待决策 |
| 会后（POST） | 纪要生成、待办提取、待办提醒、自动延期标记、下次会议自动创建、议程带入 | 可用；部分动作已可编排为 Pipeline 步骤 |
| 声纹与转写闭环 | 声纹注册、特征管理、会后说话人回写、离线校正步骤入口 | 可用；依赖音频质量与外部 ISV/LLM 结果 |
| 管理后台（Admin） | 模板/步骤 CRUD、执行触发、执行记录、步骤排序、模块化管理页面 | 可用；`pipeline`、`meetings`、`users` 模块可联合运维 |
| 跨服务协同 | meeting-server + feishu-scheduled-bot + matter-progress-core | 可用；会前对比与会后推送走独立服务链路 |

> 说明：当前版本已从“固定流程”升级为“可配置编排 + 关键链路保底”。  
> 对外部依赖较强的环节（飞书权限、ISV 音频、LLM 可用性）仍建议保留人工复核与降级策略。

---

## 2. 三场景会议音频策略（v0.18）

**背景**：纯线下会议使用本地麦克风录音；混合/纯线上会议可利用飞书视频会议的云端录音文件作为音频来源，实现纪要自动生成。

### 2.1 场景定义

- **OFFLINE（纯线下）**：默认场景。所有参会人 `attendanceMode=OFFLINE`。使用本地 PCM 录音 + 离线 ASR 校正。零改动，沿用 v0.17 流程。
- **HYBRID（混合）**：部分参会人线上、部分线下。优先本地录音；若本地音频不可用，自动下载 `sourceAudioUrl`（云端录音）作为兜底。
- **ONLINE（纯线上）**：所有参会人 `attendanceMode=ONLINE`。无本地推流，依赖云端录音 URL 进行离线处理。

### 2.2 自动推导规则

创建会议时未显式指定 `meetingScenario` 时，系统按以下规则自动计算：

1. 参会人列表为空或全部 OFFLINE → **OFFLINE**
2. 全部 ONLINE → **ONLINE**
3. 混合存在 → **HYBRID**

### 2.3 数据库字段

`int_meeting` 新增两列（执行 `v0.18-meeting-scenario-audio-fallback.sql` 后生效）：

- `meeting_scenario`：OFFLINE / HYBRID / ONLINE（数据库默认 OFFLINE）
- `source_audio_url`：云端录音文件可访问 URL（支持 MP4/M4A 等飞书录制格式，系统自动下载到 `./data/audio/{date}/cloud/` 目录）

### 2.4 会后处理流程

1. 会议结束 → 发布 `MeetingEndedEvent`（优先本地 `audioPath`，否则回退 `sourceAudioUrl`）
2. `MinuteGenerationService` 收到事件后：
   - 若有本地音频 → 直接离线 ASR
   - 否则调用 `MeetingAudioMaterializerService` 下载云端文件
   - 下载成功后复用同一套离线校正 + LLM 纪要生成链路
3. 配置开关：`meeting.audio.cloud-download-enabled=true`（默认开启）

### 2.5 注意事项

- 云端下载仅在本地音频缺失时触发；已存在本地 PCM 时优先使用。
- 下载后的文件保留 72 小时（与本地音频一致），由定时任务清理。
- 目前 `sourceAudioUrl` 需外部提供（飞书 VC API 获取录制文件 URL 的完整对接在后续迭代）。

---

## 3. 系统使用指南（实践篇）

本节提供**可直接执行**的步骤、配置示例与故障排查，帮助开发、测试、运维快速上手三场景能力。

### 3.1 创建会议时指定/推导场景

**方式一：自动推导（推荐）**

创建会议时仅需在 `participants` 数组中指定 `attendanceMode`：

```json
POST /api/v1/meetings
{
  "title": "Q2 经营分析会",
  "company": "吉青汽车科技集团",
  "groupName": "经营委员会",
  "presetTypeCode": 5,
  "participants": [
    {"userId": "ou_xxx1", "name": "张三", "attendanceMode": "OFFLINE"},
    {"userId": "ou_xxx2", "name": "李四", "attendanceMode": "ONLINE"},
    {"userId": "ou_xxx3", "name": "王五", "attendanceMode": "OFFLINE"}
  ]
}
```

系统自动计算场景并在响应中返回 `meetingScenario`。

**方式二：显式指定场景**

```json
{
  "title": "纯线上战略评审",
  "meetingScenario": "ONLINE",
  "sourceAudioUrl": "https://open.feishu.cn/.../recording/xxx.mp4",
  "participants": [
    {"userId": "ou_xxx1", "name": "张三", "attendanceMode": "ONLINE"},
    {"userId": "ou_xxx2", "name": "李四", "attendanceMode": "ONLINE"}
  ]
}
```

### 3.2 纯线上/混合场景完整流程

1. 创建会议时传入 `sourceAudioUrl`（或由飞书 bot 后续回写）。
2. 线上参会人通过个人 `joinUrl` 完成检点。
3. 操作员点击「结束会议」。
4. 系统自动判断：本地音频存在 → 直接使用；否则下载云端文件 → 离线 ASR → LLM 纪要 → 飞书文档。
5. 飞书卡片推送纪要链接。

### 3.3 关键配置项

| 配置项 | 默认值 | 说明 | 建议 |
|--------|--------|------|------|
| `meeting.audio.cloud-download-enabled` | `true` | 是否允许下载云端录音 | 生产环境保持 true |
| `meeting.audio.cache-dir` | `./data/audio` | 本地音频与云端下载根目录 | 确保存储空间充足 |
| `meeting.audio.cache-retention-hours` | `72` | 下载文件保留时长 | 与本地音频一致 |

### 3.4 运行时行为与日志

**成功日志示例**：
```
INFO  MeetingAudioMaterializerService - Cloud audio materialized: meetingId=xxx, path=./data/audio/2026-05-27/cloud/xxx-cloud.m4a
```

**失败降级日志示例**：
```
WARN  MeetingAudioMaterializerService - Failed to materialize cloud audio: ... err=404 Not Found
```

### 3.5 故障排查 checklist

| 现象 | 可能原因 | 排查入口 |
|------|----------|----------|
| 纪要内容为空 | `sourceAudioUrl` 无效或下载失败 | `meeting-server` 日志 `MeetingAudioMaterializerService` |
| 云端文件未下载 | 配置开关关闭 | 检查 `MEETING_AUDIO_CLOUD_DOWNLOAD_ENABLED` |
| 下载后文件为 0 字节 | 飞书录制文件权限不足 | 确认 URL 可带 token 访问 |
| 场景显示错误 | `attendanceMode` 拼写错误 | 检查建会接口返回的参会人列表 |

### 3.6 运维与监控建议

- 存储监控：关注 `./data/audio/cloud/` 目录增长。
- 定时清理：已有 `cleanAudioCache` 任务（每天 3:00）。
- Admin UI：在 `meeting-admin-server` 「meetings」模块可查看 `meetingScenario` 与 `sourceAudioUrl` 字段。

### 3.7 Pipeline 编排使用（Admin）

> 入口：`meeting-admin-server` -> `#/pipeline`。  
> 目标：将会前/会中/会后动作配置为可复用步骤链。

#### 3.7.1 使用顺序

1. 创建模板（选择 `PRE` / `MID` / `POST`）。
2. 新增步骤并设置顺序（支持拖拽/上移/下移）。
3. 配置每个步骤 `configJson`（可含 `condition` 条件）。
4. 手动触发执行：`pre-agenda-owner-confirm-notify` 推荐输入 `presetTypeCode` 直接按预设触发（不要求会议已创建）；其他步骤仍可输入 `meetingId` 单场触发；或由调度器自动触发 PRE。
5. 在执行记录查看状态：`PENDING / RUNNING / SUCCESS / FAILED / WAITING_CALLBACK / TIMEOUT`。

#### 3.7.2 常用 stepType（按阶段）

- `PRE`：`pre-confirm-card`、`pre-confirm-persist`、`pre-inventory-card`、`pre-push-doc-link`、`pre-voiceprint-check`、`pre-agenda-notify`、`pre-agenda-confirm`、`pre-calendar-create`、`pre-agenda-fill-init`、`pre-agenda-fill-notify`、`pre-agenda-owner-confirm-notify`
- `MID`：`mid-topic-timeout`、`mid-prev-progress-tts`
- `POST`：`post-todo-remind`、`post-auto-delayed`、`post-auto-next-meeting`、`post-feishu-task-sync`、`post-todo-action`、`post-agenda-carry`、`post-voiceprint-identify`、`post-offline-llm-correction`
- 通用：`preset-sync`、`settings-reload`、`weekly-job`、`push-notification`

#### 3.7.3 配置示例

**条件分支（仅在会议状态为 INVITED 时执行）：**

```json
{
  "condition": {
    "key": "meeting.status",
    "equals": "INVITED"
  }
}
```

**消息推送（动态模板 + 指定目标）：**

```json
{
  "targetType": "CHAT",
  "messageTemplate": "会前提醒：{meetingTitle}（{meetingId}）请确认参会。"
}
```

**会中议题超时策略：**

```json
{
  "strategy": "AUTO_NEXT",
  "topicWarnMinutes": 3
}
```

**会前会序资料回填（个人/群/混合通知）：**

```json
{
  "presetCode": 1,
  "expireMinutes": 180,
  "leaderUserIds": ["ou_leader_1"]
}
```

> 分发规则已升级：`pre-agenda-fill-init` 会自动从 `host_agenda.items[].owners` 读取负责人（可空、可多个），不再要求在流水线里手写 `participantAgenda`。
> 回填提交默认更新**当前会议**的 `host_agenda`（meeting 维度），不再默认回写全局 preset。
> leader 自动授权规则（严格模式）：从模板 `leader_name`（姓名）在“本次会议参会人”中做唯一匹配；仅唯一命中时授予 leader 全会序权限，未命中/重名均不授权。
>
> `host_agenda` 示例：
>
> ```json
> {
>   "version": 2,
>   "items": [
>     { "title": "会序1", "minutes": 10, "owners": ["ou_user_a"] },
>     { "title": "会序2", "minutes": 5, "owners": ["ou_user_b", "ou_user_c"] }
>   ]
> }
> ```

```json
{
  "entryUrl": "https://oa.qdyhjz.cn/meeting-server/agenda-fill.html",
  "sendParticipantUsers": true,
  "groupClaimCard": true,
  "groupInlineFillCard": false,
  "groupIds": ["oc_xxx_group_a"],
  "fallbackMeetingChat": true,
  "extraUserIds": ["ou_ops_1"],
  "userMessageTemplate": "请在会前完成你负责会序资料回填：{fillUrl}",
  "groupMessageTemplate": "会前会序资料回填已发起，请相关同事打开个人通知中的回填链接完成提交。"
}
```

> 建议：开启 `groupClaimCard=true`，群里只发“领取我的回填链接”按钮，用户点击后系统按卡片回调里的 `user_id` 动态私聊专属 token 链接，避免群内泄露可编辑入口。
> 若希望在群内直接填写而不跳转，可设置 `groupInlineFillCard=true`（会序编号/分钟/URL 在群卡片直接提交）。

**会前定时只发对应人（一步到位）**

```json
{
  "entryUrl": "https://oa.qdyhjz.cn/meeting-server/agenda-fill.html",
  "expireMinutes": 180,
  "leaderUserIds": ["ou_leader_1"],
  "userMessageTemplate": "请确认并完善你负责的会序：{fillUrl}\\n会议：{meetingTitle}"
}
```

> 使用 `pre-agenda-owner-confirm-notify` 时，系统会在执行时自动：
> 1) meeting 模式：从当前会议 `host_agenda.items[].owners` 识别对应人；
> 2) preset 直触发模式：从预设 `host_agenda.items[].owners` 识别对应人；
> 3) 给每人发专属可编辑链接；
> 4) 对应人提交后按权限更新对应目标（meeting 或 preset）。
> 5) leader 权限可通过 `leaderUserIds` 明确指定；meeting 模式下可叠加 `leader_name` 唯一匹配规则。

#### 3.7.4 异步回调步骤说明

- 诸如 `pre-confirm-card` / `pre-agenda-confirm` 会先发卡片，再进入 `WAITING_CALLBACK`。
- 用户点击卡片按钮后，系统通过回调路由继续推进后续步骤。
- 若长时间未回调，会被超时扫描任务标记为 `TIMEOUT`，可在后台重试或人工补偿。

#### 3.7.5 触发器推荐配置（dev / prod）

> 你的当前策略：**会中（MID）暂不加自动触发器**。  
> 推荐做法：只启用 `PRE` 与 `POST` 自动触发，`MID` 继续人工/外部触发。

| 场景 | 配置项 | 推荐值 | 说明 |
|------|--------|--------|------|
| dev | `meeting.scheduler.pre-enabled` | `true` | 开启 PRE 自动触发 |
| dev | `meeting.scheduler.scan-ms` | `60000` | 1 分钟扫描，便于联调观察 |
| dev | `meeting.scheduler.pre-window-minutes` | `5` | 避免扫描漂移导致错过触发 |
| dev | `meeting.scheduler.pre-24h-template-code` | `pre_24h_default` | 会前 24h 模板 |
| dev | `meeting.scheduler.pre-10m-template-code` | `pre_10m_default` | 会前 10min 模板 |
| dev | `meeting.pipeline.post-auto-trigger.enabled` | `true` | 会议结束后自动触发 POST |
| dev | `meeting.pipeline.post-auto-trigger.template-code` | 空 | 用默认 POST 启用模板 |
| prod | `MEETING_SCHEDULER_PRE_ENABLED` | `true` | 同 dev |
| prod | `MEETING_SCHEDULER_SCAN_MS` | `300000` | 建议 5 分钟，减少调度压力 |
| prod | `MEETING_SCHEDULER_PRE_WINDOW_MINUTES` | `5` | 与扫描频率配套 |
| prod | `MEETING_SCHEDULER_PRE_24H_TEMPLATE_CODE` | 按模板命名 | 例如 `pre_24h_default` |
| prod | `MEETING_SCHEDULER_PRE_10M_TEMPLATE_CODE` | 按模板命名 | 例如 `pre_10m_default` |
| prod | `MEETING_PIPELINE_POST_AUTO_TRIGGER_ENABLED` | `true` | 同 dev |
| prod | `MEETING_PIPELINE_POST_TEMPLATE_CODE` | 可空 | 若需固定流程再填写 |

生产环境变量示例：

```bash
MEETING_SCHEDULER_PRE_ENABLED=true
MEETING_SCHEDULER_SCAN_MS=300000
MEETING_SCHEDULER_PRE_WINDOW_MINUTES=5
MEETING_SCHEDULER_PRE_24H_TEMPLATE_CODE=pre_24h_default
MEETING_SCHEDULER_PRE_10M_TEMPLATE_CODE=pre_10m_default
MEETING_PIPELINE_POST_AUTO_TRIGGER_ENABLED=true
MEETING_PIPELINE_POST_TEMPLATE_CODE=
```

#### 3.7.6 完全零基础操作指南

如果执行人不懂技术、只在管理后台操作，请直接按文档逐步点击配置：

- [会前流程编排指南-零基础.md](会前流程编排指南-零基础.md)

该文档提供了：

- 后台点击路径（模板、步骤、执行）
- 会前 24h / 10min 推荐流程
- 可直接复制的步骤配置
- 上线前检查清单与故障判断方法

---

## 4. 角色与职责

| 角色 | 主要操作 | 入口 |
|------|----------|------|
| **会务/发起人** | 创建会议、维护参会人、结束会议（备用） | 飞书机器人「会议管理」、dashboard 工作台 |
| **现场录音操作员** | 会议主页·录音模块：推流、结束会议 | 飞书卡片链接 `/rec` |
| **会议主持** | 会议主页·主持模块：开始、检点、下一议题 | `/host/{id}` |
| **系统管理员** | 配置 Pipeline 模板、步骤、执行与排障 | `meeting-admin-server` -> `#/pipeline` |

---

## 5. 常见问题与故障排查

- **Q**：纯线上会议为什么没有本地录音文件？  
  **A**：纯线上场景默认不推流，依赖 `sourceAudioUrl` 兜底。创建时务必传入可访问的云端录音 URL。

- **Q**：云端下载失败怎么办？  
  **A**：检查日志中的 `MeetingAudioMaterializerService` 错误信息；确认 URL 可公开访问或携带有效 token；必要时手动上传本地 PCM 作为降级。

- **Q**：如何在 Admin UI 查看会议场景？  
  **A**：进入 `meeting-admin-server` → 「meetings」模块，详情页会展示 `meetingScenario` 与 `sourceAudioUrl` 字段。

- **Q**：Pipeline 执行卡在 `WAITING_CALLBACK` 怎么办？  
  **A**：先确认飞书卡片回调地址和 token 配置正确；确认用户已点击按钮。若超过超时窗口会自动转 `TIMEOUT`，可在后台重跑该阶段。

- **Q**：为什么某个步骤没有执行但显示成功？  
  **A**：通常是命中了 `configJson.condition` 的“不满足”分支，会以“条件不匹配跳过”方式记为成功，避免阻塞后续步骤。

- **Q**：议题到时为什么会自动跳下一议题？  
  **A**：因为配置了 `mid-topic-timeout` 的 `strategy=AUTO_NEXT`；如需人工决定改为 `WAIT_DECISION`。

---

## 6. 附录

### 6.1 常用 API 一览（三场景相关）

| 接口 | 方法 | 关键字段 | 说明 |
|------|------|----------|------|
| 创建会议 | POST `/api/v1/meetings` | `meetingScenario`, `sourceAudioUrl`, `participants[].attendanceMode` | 显式或自动推导场景 |
| 查询会议 | GET `/api/v1/meetings/{id}` | 返回 `meetingScenario`, `sourceAudioUrl` | 前端展示用 |
| 结束会议 | POST `/api/v1/meetings/{id}/end` | 触发云端兜底逻辑 | 无需额外参数 |
| 触发 Pipeline | POST `/api/v1/admin/pipeline/execute` | `presetTypeCode`（推荐）/`meetingId`（可选）, `stage`, `templateCode`, `skipExisting` | 管理后台触发编排执行（code 维度优先） |
| 回填会话查询 | GET `/api/v1/agenda-fill/session?token=...` | `token` | 获取当前 token 可编辑会序项 |
| 回填提交 | POST `/api/v1/agenda-fill/submit` | `token`, `updates[]` | 回写 preset 的 `host_agenda` |

### 6.2 相关文档

- [meeting-admin.md](meeting-admin.md) — 管理后台使用说明
- [PRD-Java-二期.md](PRD-Java-二期.md) — 产品需求与架构主文档
- `schema-upgrade/v0.18-meeting-scenario-audio-fallback.sql` — DDL 升级脚本

---

*本手册随代码版本同步更新。当前版本已补齐 Pipeline 二期能力（条件分支、异步回调、跨步骤上下文、自动触发 PRE），并保留三场景音频兜底链路。*
模板一：pre_24h_default（会前24小时）
阶段：PRE
模板编码：pre_24h_default
建议步骤顺序：
Step 1
stepType: pre-agenda-owner-confirm-notify
stepCode: owner_confirm_notify_24h
orderNo: 10
timeoutSeconds: 120
configJson:
{
  "entryUrl": "https://oa.qdyhjz.cn/meeting-server/agenda-fill.html",
  "expireMinutes": 1440,
  "leaderUserIds": ["ou_leader_1"],
  "userMessageTemplate": "【会前24小时】请确认并完善你负责的会序：{fillUrl}\n会议：{meetingTitle}"
}
Step 2（可选）
stepType: pre-agenda-notify
stepCode: agenda_notify_24h
orderNo: 20
timeoutSeconds: 120
configJson:
{}
模板二：pre_10m_default（会前10分钟）
阶段：PRE
模板编码：pre_10m_default
建议步骤顺序：
Step 1
stepType: pre-agenda-owner-confirm-notify
stepCode: owner_confirm_notify_10m
orderNo: 10
timeoutSeconds: 120
configJson:
{
  "entryUrl": "https://oa.qdyhjz.cn/meeting-server/agenda-fill.html",
  "expireMinutes": 120,
  "leaderUserIds": ["ou_leader_1"],
  "userMessageTemplate": "【会前10分钟】请立即确认并完善你负责的会序：{fillUrl}\n会议：{meetingTitle}"
}
Step 2（可选）
stepType: pre-inventory-card
stepCode: inventory_10m
orderNo: 20
timeoutSeconds: 120
configJson:
{}
调度配置（确保自动触发）
在 meeting-server 配置里确认：

meeting:
  scheduler:
    pre-enabled: true
    pre-window-minutes: 5
    pre-24h-template-code: pre_24h_default
    pre-10m-template-code: pre_10m_default
上线前检查（1分钟）
每个会序 host_agenda.items[].owners 已填 user_id
机器人有给用户私聊权限
entryUrl 可从飞书打开（HTTPS、可访问）
手动执行一次 PRE 验证消息是否成功送达
如果你要，我可以下一步给你一份“最简 owners 填写规范”（给会务同事直接照抄用）。