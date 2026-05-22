# 智能会议系统 — 用户手册

本文档面向 **会务/会议发起人**、**现场录音操作员**、**会议主持**、**线上线下参会人**、**运维部署人员**，说明如何通过飞书机器人与 Web 页面完成从建会、会中会议主页（录音 / 会序 / 主持三模块）、混合检点，到会后纪要查阅的完整流程。

技术策略与开发说明见 [docs/README.md](README.md) 中的产品/评估文档；**混合检点**细节亦见 [product/混合参会检点-用户手册.md](product/混合参会检点-用户手册.md)（与本手册 §7.4 主持模块、第 9 章内容同步）。

**文档版本：** 2026-05-22（会议主页 + 三模块术语；含会后通知走 feishu-scheduled-bot）· 与当前 `smart-meeting-java` 代码实现对齐。

---

## 目录

1. [产品简介](#1-产品简介)
2. [角色与职责](#2-角色与职责)
3. [环境与部署要点](#3-环境与部署要点)
4. [飞书机器人使用](#4-飞书机器人使用)
5. [创建会议](#5-创建会议)
6. [会前准备](#6-会前准备)
7. [会中：会议主页](#7-会中会议主页)
  - [7.4 主持模块：流程开关与推荐组合](#74-主持模块流程开关与推荐组合)
9. [会中：混合参会检点](#9-会中混合参会检点)
10. [会后：纪要、待办与纠错](#10-会后纪要待办与纠错)（含 [10.5 Bot 通知](#105-会后飞书通知与-feishu-scheduled-bot方案二)）
11. [声纹注册](#11-声纹注册)
12. [Web 页面与链接说明](#12-web-页面与链接说明)
13. [常见问题](#13-常见问题)
14. [快速检查清单](#14-快速检查清单)
15. [附录](#15-附录)

---

## 1. 产品简介

**智能会议系统（smart-meeting-java）** 以 **飞书群聊/单聊** 为入口，由 Spring Boot 服务提供会议创建、现场录音推流、讯飞实时 ASR、AI 主持人 TTS 播报、混合线上线下检点、LLM 纪要生成等能力。用户主要通过：

- **飞书**：发送文字指令、点击卡片/菜单；
- **浏览器**：会议主页（`/rec` / `/host`）、会务选会页、线上到会确认页、声纹注册页。

### 1.1 典型一场会的时间线

```text
[会务] 飞书「开始会议」→ 选类型创建 → 群内「会议已开始」卡片
    ↓
[操作员/主持] 打开会议主页（飞书卡片链至 /rec 或单独取 /host 链）
    ↓
[操作员/主持] 点击「开始会议」→ 同时启动录音推流 + AI 主持 → 开场 TTS →（可选）混合检点
    ↓
[线上同事] 飞书单聊个人链接 → 自动「已到会」
[线下同事] 听点名 → 现场麦答到
    ↓
[主持] 主持模块：下一议题、加时；会序模块：资料与 OpenClaw 通报；录音模块：底部实时字幕
    ↓
[操作员/主持] 会议主页「结束会议」→ 纪要生成 → 飞书推送文档链接
```

### 1.2 当前能力边界（使用前请知悉）


| 能力                     | 状态     | 说明                                             |
| ---------------------- | ------ | ---------------------------------------------- |
| 飞书建会 + 固定会务类型 1～5      | ✅ 可用   | 含预设参会人名单，支持 AI 开场与检点                           |
| 混合线上线下检点               | ✅ 可用   | 线上个人链接 + 线下点名                                  |
| AI 主持 TTS、议题切换、加时、计时提醒 | ✅ 可用   | 议题到时 **提醒** 后需 **手动**「下一议题」                    |
| 现场录音 + 实时 ASR          | ✅ 可用   | 可用 `meeting.asr.realtime-enabled=false` 关闭实时转写；发言人多为 Speaker N |
| 会后通知经 scheduled-bot    | ✅ 可选   | 默认直连飞书；`MEETING_NOTIFY_BOT=true` 走 `/api/push` |
| 会序模块 · OpenClaw 通报         | ✅ 可用   | 须配置表同行 `openclaw_briefing=1` 且 `feishu_doc_url` 有效 |
| 周期会议自动创建、会前确认、待办闭环     | 📋 规划中 | 见「项目最终形态」愿景文档，非本手册操作范围                         |


---

## 2. 角色与职责


| 角色            | 主要操作                       | 入口                         |
| ------------- | -------------------------- | -------------------------- |
| **会务/发起人**    | 创建会议、维护参会人及线上线下属性、结束会议（备用） | 飞书机器人、会务选会页                |
| **现场录音操作员**   | 会议主页 · **录音模块**：拾音推流、结束会议   | 群内「会议已开始」卡片中的 **会议主页链接**（`/rec`） |
| **会议主持**       | 会议主页 · **主持模块**：开始会议、检点、下一议题/加时 | `/host/{会议ID}?token=…`（与 `/rec` 同页，JWT 类型不同） |
| **线下参会人**     | 被点名后现场麦答到                  | 无需单独网页                     |
| **线上参会人**     | 打开 **个人入会链接** 登记到场         | 飞书单聊「线上到会确认」卡片             |
| **全体参会人**     | 会前注册声纹、会后查看纪要              | 飞书「注册声纹」「查看纪要」             |
| **运维**        | 部署服务、配置 HTTPS/讯飞/飞书、数据库迁移  | 服务器、`application.yml`、环境变量 |


### 2.1 会议主页与三模块

会中核心入口为 **会议主页**（同一套 Web 页面 `host-meeting.html`）：

| 浏览器路径 | 名称 | JWT 类型 | 典型下发方 |
| ---------- | ---- | -------- | ---------- |
| `/rec/{meetingId}?token=…` | 会议主页（操作员入口） | `recording` | 飞书「会议已开始」卡片 |
| `/host/{meetingId}?token=…` | 会议主页（主持入口） | `host` | `GET …/host-url` 或会务单独取链 |

**无需打开两个浏览器页**：操作员与主持可在同一会主页完成录音、会序查阅与 AI 主持；`/rec` 与 `/host` 仅链接与凭证类型不同。

主页内三个 **功能模块**（同屏分区，非独立 URL）：

| 模块 | 页面区域 | 主要能力 | 典型角色 |
| ---- | -------- | -------- | -------- |
| **录音模块** | 点击「开始会议」后隐式推流；底部 **实时字幕** | `WS /ws/audio`；暂停/继续；`POST …/recording-session/end` | 现场操作员 |
| **会序模块** | 左侧 **会序参考资料**；右侧 **议程详情** | 飞书资料外链；`openclawBriefing` 通报 Markdown；`int_matter_progress_doc_config` | 主持 / 会务 |
| **主持模块** | **议程进度**（开始/暂停/结束、下一议题、加时、检点）；**主持数字人** | `POST /api/v1/host/meetings/…/*`；`WS /ws/host` | 主持 |

**遗留入口**：`/static/index.html` 为旧版「仅录音 + 会序外链」窄布局，**非**飞书卡片主路径；生产请使用 `/rec` 或 `/host`。

#### 2.1.1 进度通报相关术语（与会主页模块区分）

为避免「会议进度通报」混称，下列为 **独立** 能力（勿与「录音模块」等同）：


| 名称 | 粒度 | 展示位置 | 触发条件 |
| ---- | ---- | -------- | -------- |
| **会序 OpenClaw 通报** | 每个会序（进行中时） | 会序模块 · 会序参考资料区 | `openclawBriefing=true` 且该会序有飞书 URL |
| **会前上次待办进度** | 上一场会的待办 | 建会时飞书卡片（非会中主页） | `GET …/previous-progress` / `progress-analysis` Skill |

- 会序 OpenClaw 通报由 `AgendaBriefingService` + `matter-progress` Skill 生成；旧「录音页手动事项通报」已移除。
- **`int_matter_progress_doc_config`**：按 preset + `agenda_index` 配置；**仅当**同行 `openclaw_briefing=1` 且 `feishu_doc_url` 有效时调用 OpenClaw。

---

## 3. 环境与部署要点

### 3.1 运行依赖


| 依赖       | 说明                                  |
| -------- | ----------------------------------- |
| JDK      | 17+                                 |
| MySQL    | 会议、参会人、转写、纪要等持久化                    |
| Redis    | 缓存与会话（生产建议开启）                       |
| Kafka    | 可选；关闭时纪要等走本地降级                      |
| 公网 HTTPS | `meeting.base-url` 须为飞书与用户浏览器可访问的地址 |
| 讯飞       | 实时 ASR、在线 TTS；声纹 ISV 可选             |
| 飞书自建应用   | 发消息、卡片、拉群/单聊等权限                     |
| LLM      | 纪要生成（如 DeepSeek）                    |


### 3.2 快速启动（运维）

```bash
# 配置环境变量后
docker compose up -d

# 或本地开发
./mvnw -pl meeting-server spring-boot:run
```

默认 HTTP 端口 **8765**；健康检查：`GET /api/v1/health`。

全量 DDL：`meeting-server/src/main/resources/schema.sql`；增量脚本见项目根目录 `sql/migration_*.sql`（如混合参会字段 `migration_20260515_hybrid_attendance.sql`）。

### 3.3 关键配置（环境变量）


| 变量 / 配置项                                                           | 说明                                                                                               |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| `BASE_URL` / `meeting.base-url`                                    | 公网 HTTPS，用于生成 `/rec`、`/host`、`/join` 链接                                                          |
| `FEISHU_APP_ID` / `FEISHU_APP_SECRET`                              | 飞书应用凭证                                                                                           |
| `MEETING_HOST_ENABLED`                                             | AI 主持总开关（`meeting.host.enabled`），`false` 时无法 `POST …/start`，默认 `true`                            |
| `MEETING_HOST_AGENDA_ENABLED`                                      | 会序推进（下一议题/跳过/加时），默认 `true`                                                                       |
| `MEETING_HOST_TTS_ENABLED`                                         | 主持 TTS 与 `tts_`* WebSocket，默认 `true`；`false` 为轻量主持（仅计时与 `host_state`）                            |
| `MEETING_HOST_ROLL_CALL_ENABLED`                                   | 混合检点，默认 `true`                                                                                   |
| `MEETING_HOST_AUTO_ROLL_CALL_AFTER_OPENING`                        | 开场后自动检点（须检点开启且预设有应到名单），默认 `true`                                                                 |
| `MEETING_HOST_ROLL_CALL_ONLINE_INVENTORY_SEC`                      | 线上盘点等待秒数，默认 60                                                                                   |
| `MEETING_HOST_ROLL_CALL_WINDOW_SEC`                                | 线下每人答到窗口秒数，默认 12                                                                                 |
| `ISV_ENABLED`                                                      | 声纹识别，默认 `false`                                                                                  |
| `XFYUN_*`                                                          | 讯飞 ASR/TTS                                                                                       |
| `MEETING_ASR_REALTIME_ENABLED` / `meeting.asr.realtime-enabled`  | 会中 **实时转写**（默认 `true`）；`false` 时仅录音，会后走离线 ASR                                              |
| `LLM_*`                                                            | 纪要 LLM                                                                                           |
| `MEETING_NOTIFY_BOT` / `meeting.notification.bot-enabled`          | 会后飞书通知是否经 **feishu-scheduled-bot** `POST /api/push`（默认 `false`）                                  |
| `MEETING_NOTIFY_BOT_URL` / `meeting.notification.bot-base-url`     | Bot 基地址，默认 `http://127.0.0.1:8764`                                                               |
| `MEETING_NOTIFY_BOT_API_KEY` / `meeting.notification.bot-api-key`  | 与 Bot 的 `feishu.api-key` / `X-API-Key` 一致                                                        |
| `MEETING_NOTIFY_FALLBACK` / `meeting.notification.fallback-direct` | Bot 失败时是否回退直连飞书（默认 `true`）                                                                       |
| `OPENCLAW_AGENT_PROVIDER`                                          | AI Agent 提供者：`llm`（默认，直调 LLM）/ `mcp`（Gateway WebSocket + MCP + Skill，推荐） |
| `OPENCLAW_ENABLED`                                                 | OpenClaw 总开关（`provider=mcp` 时需设为 true） |
| `OPENCLAW_GATEWAY_URL` / `OPENCLAW_SESSION_KEY` / `OPENCLAW_AUTH_TOKEN` / `OPENCLAW_DEVICE_TOKEN` | Gateway 连接（仅 `mcp`）；跨机访问需 `device-token` |
| `OPENCLAW_SKILL_MODE`                                              | Skill 模式开关（仅 `mcp`）：`true`（默认，精简 prompt）/ `false`（完整 prompt） |
| `JWT_SECRET`                                                       | 生产务必更换                                                                                           |


完整默认值见 `meeting-server/src/main/resources/application.yml`。

### 3.4 飞书开放平台订阅建议


| 事件 / 能力                                        | 用途                                                                             |
| ---------------------------------------------- | ------------------------------------------------------------------------------ |
| `im.message.receive_v1`                        | 接收「开始会议」等文字指令                                                                  |
| `card.action.trigger`（或 v1）                    | 会务类型卡片、纪要卡片按钮                                                                  |
| `application.bot.menu_v6`                      | 快捷菜单「开始会议」（`event_key` 与 `menu-event-key-start-meeting` 一致，默认 `start_meeting`） |
| `im.chat.access_event.bot_p2p_chat_entered_v1` | 可选；用户进入与机器人单聊时建立会话                                                             |
| 发消息、发卡片                                        | 群通知、线上个人链接单聊                                                                   |


菜单若配置为「向当前会话发送消息：开始会议」，则走消息事件即可，不必依赖 `menu_v6`。

---

## 4. 飞书机器人使用

### 4.1 首次使用

1. 将机器人拉入 **目标群**（或在与机器人的 **单聊** 中操作）。
2. 群内发送 **「开始会议」** 或点击机器人 **快捷菜单**。
3. 部分群会在首次使用时收到 **「智能会议 · 操作说明」** 卡片（可在配置中关闭 `instruction-card-on-first-start`）。

### 4.2 指令一览

在群内或与机器人的会话中发送以下文字（发送 **「帮助」** 可查看简要列表）：


| 指令                      | 说明                                          |
| ----------------------- | ------------------------------------------- |
| `开始会议`                  | 打开 **会务选会 Web 页**（推荐）                       |
| `开始会议 1` … `开始会议 5`     | 按吉青固定会务类型 **直接创建**（类型 1～5）                  |
| `开始会议 6`                | 创建「其他会议」，下一行回复主题或 `主题:xxx`                  |
| `开始会议 6 主题:xxx`         | 直接创建其他会议                                    |
| `会议类型 N`                | 同 `开始会议 N`（N=1～6）                           |
| `开始会议 主题:xxx 参会人:张三,李四` | 完全自定义（参会人默认 **线下**，`userId` 为占位，**不适合混合会**） |
| `结束会议`                  | **备用**；主路径为会议主页「结束会议」                        |
| `结束会议 会议ID:uuid`        | 指定会议结束                                      |
| `查看纪要` / `查看纪要 最近10条`   | 列出近期纪要卡片                                    |
| `注册声纹 姓名:张三`            | 获取声纹注册 H5 链接                                |
| `加入会议 会议ID:uuid`        | 获取录音/入会相关说明（按实现返回）                          |
| `修改说话人 uuid 旧名:新名`      | 纪要说话人纠错                                     |
| `重新生成 会议ID:uuid`        | 重新触发纪要生成                                    |
| `帮助` / `help`           | 显示指令帮助                                      |


### 4.3 建会后的飞书消息


| 消息           | 接收人                                  | 内容                            |
| ------------ | ------------------------------------ | ----------------------------- |
| 会议已开始        | **群聊**                               | 主题、时间、**会议主页**链接（`/rec`，操作员用）       |
| 线上到会确认       | 每位 `ONLINE` 且有效 `open_id` 的用户 **单聊** | **个人入会链接**                    |
| 纪要已生成        | 群聊                                   | 飞书文档链接（卡片）；可走 Bot 或直连，见 §10.5 |
| 待办已同步 / 待办进度 | 群聊                                   | 待办摘要或上一场进度卡片（可选 Bot）          |


群内 **不会** 张贴所有参会人的个人链接。

### 4.4 菜单无响应时的排查

- 确认 `meeting.base-url` 为 **HTTPS** 且飞书可访问。
- 菜单为「推送 event_key」时，须订阅 `application.bot.menu_v6`，且 `event_key` 与后台 `start_meeting` 一致。
- 无 `chat_id` 时，系统会尝试用户 **最近在群内发过消息的群**；否则在单聊中提示：请先在目标群发消息后再点菜单。

---

## 5. 创建会议

### 5.1 方式对比


| 方式                       | 适用场景     | 参会人 / 混合会                              |
| ------------------------ | -------- | -------------------------------------- |
| **Web 会务选会页**（`开始会议` 卡片） | 日常会务、混合会 | 预设名单；混合会需在 API 层补 `ONLINE` + `open_id` |
| `开始会议 1`～`5`             | 固定周会/月会等 | 使用库表 `int_meeting_type_preset` 中的名单    |
| `开始会议 6`                 | 临时主题会    | 无预设名单                                  |
| `开始会议 主题:… 参会人:…`        | 快速临时会    | 仅姓名，**无** 飞书 `open_id`，**不能** 推个人链接    |


### 5.2 固定会务类型（1～5）


| 代码  | 名称         | 说明         |
| --- | ---------- | ---------- |
| 1   | 综合管理会（周会）  | 每周一 9:30 等 |
| 2   | 技术委员会（周会）  | 技术议题       |
| 3   | 市场经营会（月会）  | 经营汇报       |
| 4   | 财务月会       | 财务汇报       |
| 5   | 经营委员会（半年会） | 经营分析       |


创建后会议带 `preset_type_code`，会议主页可加载预设议程与应到名单（用于开场与检点）。

### 5.3 Web 会务选会页流程

1. 飞书点击「打开会务选会页面」→ 浏览器打开 `/start-meeting.html?token=…`（链接短时有效）。
2. 选择类型 1～5，或 **6 — 其他会议** 并填写主题。
3. 提交后系统创建并启动会议，跳转 **会议主页**（`/rec`）；同时向飞书群推送「会议已开始」。

### 5.4 API 创建（集成 / 混合会推荐）

```http
POST /api/v1/meetings
Content-Type: application/json
```

```json
{
  "title": "集团月度经营分析会",
  "presetTypeCode": 1,
  "participants": [
    { "userId": "ou_xxxx", "name": "张三", "attendanceMode": "OFFLINE" },
    { "userId": "ou_yyyy", "name": "李四", "attendanceMode": "ONLINE" }
  ]
}
```


| 字段               | 说明                       |
| ---------------- | ------------------------ |
| `userId`         | 飞书 `open_id`；线上参会 **必填** |
| `name`           | 显示姓名                     |
| `attendanceMode` | `OFFLINE`（默认）或 `ONLINE`  |


创建并启动后调用方或飞书流程需向操作员、主持、线上同事分发对应链接（见第 12 章）。

---

## 6. 会前准备

### 6.1 数据库

生产库需包含：

- 基础表：`int_meeting`、`int_meeting_participant` 等（见 `schema.sql`）。
- 混合参会：`attendance_mode`、`checked_in_at`、`check_in_source`（`migration_20260515_hybrid_attendance.sql`）。
- 会务预设：`int_meeting_type_preset`（`migration_20260510_meeting_type_preset.sql`）。

### 6.2 会务 checklist

- `BASE_URL` 为公网 HTTPS
- 飞书应用已安装到企业，机器人已入群
- 混合会：每位线上人员 `ONLINE` + 正确 `open_id` + 姓名
- 会务类型 1～5：预设议程与应到名单已在库中配置
- 主持会序需飞书资料或 OpenClaw 通报：在 `host_agenda` 写 `feishuDocUrl` / `openclawBriefing`，或配置 `int_matter_progress_doc_config`（按 preset + `agenda_index` 补 URL）
- 建议参会人提前 **注册声纹**（提升纪要说话人识别）

### 6.3 飞书权限

- 机器人可向 **群**、**用户单聊** 发消息与卡片。
- 线上个人链接依赖 **单聊**；`userId` 必须为 `**open_id`**。

---

## 7. 会中：会议主页

会议主页为会中 **唯一主界面**（`host-meeting.html`），内含录音、会序、主持三模块。飞书「会议已开始」卡片默认链至 `/rec`；主持亦可单独使用 `/host` 链接（同页、不同 JWT）。

### 7.1 打开方式与链接

| 路径 | 说明 | Token |
| ---- | ---- | ----- |
| `/rec/{meetingId}?token=…` | 操作员入口（飞书卡片默认） | 录音 JWT（`type=recording`，可推流） |
| `/host/{meetingId}?token=…` | 主持入口（`GET …/host-url`） | 主持 JWT（`type=host`） |

- 页头会根据路径显示「操作员入口」或「主持入口」，**页面布局与模块相同**。
- **请勿** 将会议主页链接发给 **线上参会人** 代替个人入会链接 `/join/…`。

### 7.2 录音模块

**启动方式**：在 **主持模块** 点击 **「开始会议」** 后，系统会先建立 `WS /ws/audio` 推流，再调用 `POST /api/v1/host/meetings/…/start` 启动主持会话（一键联动，无需分两步开页）。

| 操作 | 说明 |
| ---- | ---- |
| 单设备推流 | 同一会议仅 **一个** WebSocket 音频连接有效，后连顶掉先连 |
| 麦克风 | 浏览器须授权；iOS 请保持前台（页面有提示） |
| 暂停 / 继续 | 录音模块与主持模块「暂停/继续会议」联动 |
| 实时字幕 | 页面底部固定区展示 ASR 定稿句（需 `meeting.asr.realtime-enabled=true`） |
| 结束会议 | 议程进度区 **「结束会议」** → `POST …/recording-session/end`，触发纪要生成 |

**关闭实时转写（运维）**：`MEETING_ASR_REALTIME_ENABLED=false` 或：

```yaml
meeting:
  asr:
    realtime-enabled: false
```

效果：WebSocket 下发 `asr_disabled`；音频仍缓存，会后可走离线 ASR；**底部字幕为空**；**线下检点 ASR 答到**不可用（需检点时保持 `true`）。

### 7.3 会序模块

| 区域 | 功能 |
| ---- | ---- |
| **议程详情**（右侧） | 会序列表：待进行 / 进行中 / 已完成 / 已跳过；当前议题高亮 |
| **会序参考资料**（左侧） | 当前会序飞书 docx/wiki/base **外链**；配置 `openclawBriefing` 时展示 OpenClaw 生成的 Markdown 通报 |

**OpenClaw 会序通报**：在 `int_matter_progress_doc_config` 对该会序 **同一行** 设 `feishu_doc_url` 且 **`openclaw_briefing=1`**（默认 0）。仅满足二者时调用 OpenClaw；议程/预设里的飞书链接 alone 不会触发。
- 会序进入 **RUNNING** 时异步调用 `matter-progress` Skill，经 `WS /ws/host` 刷新 `briefingMarkdown`（`briefingStatus`: `loading` / `ready` / `failed`）。
- 开关：`meeting.host.agenda-briefing.enabled`（默认 `true`）；依赖 `OPENCLAW_AGENT_PROVIDER=mcp` 与 lark-mcp 可读目标资料。
- DDL：`schema-upgrade-v0.6-matter-progress-openclaw-briefing.sql`；预设 JSON 示例：`schema-upgrade-v0.5-host-agenda-briefing-example.sql`（勿自动执行）。

```sql
-- 示例：综合管理会 preset=1，会序 index=1（前期项目汇报）仅配置表开通报
UPDATE int_matter_progress_doc_config
SET openclaw_briefing = 1
WHERE preset_type_code = 1 AND agenda_index = 1 AND config_name = 'preset1-comp-agenda-01';
```

只读拉取正文（可选集成）：`GET /api/v1/meetings/{id}/agenda-doc-content?agendaIndex=…`（录音 JWT）。

### 7.4 主持模块

| 区域 | 功能 |
| ---- | ---- |
| **议程进度** | 开始 / 暂停 / 继续 / 结束会议；下一议题、跳过；议题加时；议题/会议计时；混合检点区 |
| **主持数字人** | TTS 播报状态与口型（`WS /ws/host` 下发 PCM） |

**主持 API**（页面自动调用）：`/api/v1/host/meetings/{meetingId}/`

| 操作 | 方法路径（示意） |
| ---- | ---------------- |
| 查询状态 | `GET …/state` |
| 开始主持 | `POST …/start` |
| 下一议题 | `POST …/next-topic` |
| 跳过议题 | `POST …/skip-topic` |
| 加时 | `POST …/extend-topic` |
| 开始检点 | `POST …/roll-call/start` |
| 跳过当前点名 | `POST …/roll-call/skip` |

混合检点操作详见 [第 9 章](#9-会中混合参会检点)。

#### 7.4.1 主持模块：流程开关与推荐组合

配置前缀：`meeting.host.*`（`application.yml` 或 §3.3 环境变量）。以下为 **全局环境级** 开关。

##### 主持能力矩阵


| 配置项                            | 环境变量                                        | 默认     | 关闭后的效果                                  |
| ------------------------------ | ------------------------------------------- | ------ | --------------------------------------- |
| `enabled`                      | `MEETING_HOST_ENABLED`                      | `true` | 拒绝 `POST …/start` 及一切主持写操作              |
| `agenda-enabled`               | `MEETING_HOST_AGENDA_ENABLED`               | `true` | 禁止下一议题、跳过、加时（计时与 `host_state` 仍刷新）      |
| `tts-enabled`                  | `MEETING_HOST_TTS_ENABLED`                  | `true` | 不调用讯飞 TTS、不推送 `tts_`* WS；议题/会议到时仅页面状态变化 |
| `roll-call-enabled`            | `MEETING_HOST_ROLL_CALL_ENABLED`            | `true` | `POST …/roll-call/start` 等返回「检点功能未启用」   |
| `auto-roll-call-after-opening` | `MEETING_HOST_AUTO_ROLL_CALL_AFTER_OPENING` | `true` | 开场后不自动进入检点（可手动点「开始检点」）                  |
| `agenda-briefing.enabled`      | `MEETING_HOST_AGENDA_BRIEFING_ENABLED`      | `true` | 关闭后不再为 `openclawBriefing` 会序触发 OpenClaw 通报     |


子参数（主持开启后生效）：`agenda-briefing.timeout-seconds`（默认 120）、`reminder.topic-minutes-left`、`reminder.meeting-minutes-left`、`roll-call.window-seconds`、`roll-call.asr-grace-seconds`、`roll-call.online-inventory-seconds`。

##### 与其它流程开关的关系（节选）

以下项不在 `meeting.host` 下，但常与主持组合使用；完整默认值见 `application.yml`。


| 能力                    | 配置项                                | 默认               |
| --------------------- | ---------------------------------- | ---------------- |
| AI 主持总闸（同表 `enabled`） | `meeting.host.enabled`             | `true`           |
| 会后 Bot 通知             | `meeting.notification.bot-enabled` | `false`（见 §10.5） |
| 纪要库内持久化               | `meeting.minute.persist-enabled`   | `true`           |
| Kafka 事件总线            | `meeting.kafka.enabled`            | `false`          |
| ASR Mock（测试）          | `meeting.asr.mock-enabled`         | `false`          |
| 会中实时转写               | `meeting.asr.realtime-enabled`     | `true`（见 §7.2）   |
| AI Agent 提供者          | `openclaw.agent.provider`          | `llm`            |


会后「结束是否自动生成纪要 / 待办提取」等分步开关尚在规划中，当前结束会议后仍会走纪要流水线。

##### 推荐组合（运维）


| 场景             | `enabled` | `agenda` | `tts` | `roll-call` | `auto-roll-call` | 说明                       |
| -------------- | --------- | -------- | ----- | ----------- | ---------------- | ------------------------ |
| **标准综合管理会**    | on        | on       | on    | on          | on               | 开场 TTS + 自动检点 + 语音提醒     |
| **轻量主持（人工控场）** | on        | on       | off   | off         | —                | 会序与计时在主页；无播报、无检点        |
| **仅检点、弱播报**    | on        | on       | off   | on          | on/off           | 少见；无 TTS 时自动检点立即调度（无等播完） |
| **关闭 AI 主持**   | off       | —        | —     | —           | —                | 仅录音模块推流；勿误关 `agenda` 期望会序   |


轻量主持 YAML 示例：

```yaml
meeting:
  host:
    enabled: true
    agenda-enabled: true
    tts-enabled: false
    roll-call-enabled: false
    auto-roll-call-after-opening: false
```

##### `enabled` 与轻量主持对比


| 能力                       | `enabled=false` | 轻量主持（`enabled`+`agenda` on，`tts`/`roll-call` off） |
| ------------------------ | --------------- | ------------------------------------------------- |
| `POST …/start`           | 拒绝              | 允许                                                |
| 议程列表 / 计时 / `host_state` | 无               | 有                                                 |
| 下一议题 / 跳过 / 加时           | 无               | 有                                                 |
| TTS / 数字人                | 无               | 无                                                 |
| 混合检点                     | 无               | 无（可再单独开启 `roll-call`）                             |

> **注意**：勿将 `MEETING_HOST_ENABLED=false` 与轻量主持混淆——总开关关闭后 **无法** `POST …/start`，主持模块与会序推进 API 不可用；录音模块仍可单独推流（若前端允许）。

### 7.5 推荐操作流程

1. 操作员或主持打开 **会议主页**（飞书卡片 `/rec` 或 `/host`）。
2. 允许麦克风，点击 **「开始会议」**（同时启动录音推流 + 主持会话）：
   - 会务类型 **1～5** 且已配置应到名单：TTS **开场白** → 自动 **混合检点**（一般无需再点「开始检点」）。
   - 否则：配置名单后手动 **「开始检点」**。
3. 检点结束后，**主持模块** 使用「下一议题」/「跳过议题」推进；**会序模块** 随当前会序展示资料与 OpenClaw 通报。
4. 议题到时 **TTS 提醒**，需 **手动**「下一议题」（检点类会序结束可自动下一项）。
5. 需要时使用 **议题加时**（1/3/5/10 分钟）。
6. 全部议题结束后，点击 **「结束会议」**（录音模块与主持模块共用同一按钮）。

**轻量主持**：`MEETING_HOST_TTS_ENABLED=false`、`MEETING_HOST_ROLL_CALL_ENABLED=false`，仍可在主页查看议程计时并手动下一议题；详见 [§7.4.1](#741-主持模块流程开关与推荐组合)。

---

## 9. 会中：混合参会检点

同一场会可同时有 **线下** 与 **线上** 参会人。

### 9.1 规则摘要


| 参会方式   | 如何记为「已到」                         | 麦克风          |
| ------ | -------------------------------- | ------------ |
| **线下** | AI 主持 **逐一点名**，现场对着 **会议室单麦** 答到 | 仅操作员设备推流     |
| **线上** | 打开 **个人入会链接**，页面自动登记             | **不需要**，禁止推流 |


```text
开会 → 线上盘点（默认 60 秒）→ 线下点名（仅 OFFLINE 名单）→ 进入议题
```

**原则**

- 应到名单以 **参会人表** 为准；创建时正确标记 `OFFLINE` / `ONLINE`。
- 线上 **不能** 用共用会议主页代替个人链接。
- 混合会场 **仅一路现场音频** 进入转写。

### 9.2 线上参会人

1. 在飞书 **与机器人的单聊** 中打开 **「📲 线上到会确认」** 卡片。
2. 点击 **「打开个人入会链接」** → 页面显示 **「已到会」** 及时间。
3. 登记后可关闭页面；**勿转发** 个人链接。

未收到单聊：确认 `ONLINE` + `open_id`；或请会务调用 `GET /api/v1/meetings/{id}/participant-links` 获取 `joinUrl` 私发。

### 9.3 线下参会人

听到 **「请 XXX 答到」** 后，在主持麦附近说「答到」「在」等；会议主页 **主持模块** 检点区显示 `[线下]` 与状态。

### 9.4 主持模块检点界面


| 阶段        | 含义              |
| --------- | --------------- |
| **线上盘点中** | 等待远程同事打开个人链接    |
| **线下点名中** | 仅 `[线下]` 人员逐一点名 |
| **检点已结束** | 可再次「开始检点」发起新一轮  |



| 按钮       | 说明              |
| -------- | --------------- |
| **开始检点** | 未在检点时发起（先线上后线下） |
| **跳过当前** | 仅线下点名中，跳过当前待答到人 |


### 9.5 补发个人链接（会务）

```http
GET /api/v1/meetings/{meetingId}/participant-links
```

`attendanceMode=ONLINE` 的条目含 `joinUrl`。

---

## 10. 会后：纪要、待办与纠错

### 10.1 结束会议


| 方式            | 说明                   |
| ------------- | -------------------- |
| **会议主页「结束会议」** | **主路径**（录音模块 + 主持模块共用按钮） |
| 飞书 `结束会议`     | **备用**；仅 **发起人** 可操作 |

结束后会议状态一般为 **处理中（PROCESSING）**，后台生成纪要；完成后向会议群（或降级向发起人单聊）推送 **纪要卡片**（含文档链接）。

### 10.2 查看纪要

- 飞书发送 `**查看纪要`** 或 `**查看纪要 最近10条**`，点击卡片中的文档链接。
- API：`GET /api/v1/meetings/{id}/minute` 等（若运维开放）。

### 10.3 说话人与重新生成


| 指令                       | 说明          |
| ------------------------ | ----------- |
| `修改说话人 {会议ID} {旧名}:{新名}` | 纠正纪要中的说话人显示 |
| `重新生成 会议ID:{uuid}`       | 重新跑纪要流水线    |


### 10.4 待办

纪要生成后可提取待办；系统会向群聊推送 **「待办已同步」** 卡片（待办条数与责任人摘要）。新一场会若关联上一场，开始会议时可能推送 **「待办进度通报」** 卡片（含 AI 分析或统计 fallback）。

### 10.5 会后飞书通知与 feishu-scheduled-bot（方案二）

会后 **三类** 群消息由 `MeetingFeishuNotifier` 统一发出（实现类：`com.smartmeeting.service.notification`）：


| 事件     | eventType（Bot 侧） | 典型场景                           |
| ------ | ---------------- | ------------------------------ |
| 纪要已生成  | `MINUTE_READY`   | 录音结束 → LLM 纪要 → 飞书文档           |
| 待办已同步  | `TODO_SYNC`      | 纪要待办提取完成                       |
| 待办进度通报 | `TODO_PROGRESS`  | 新会议开始且存在 `previous_meeting_id` |


**默认行为（`meeting.notification.bot-enabled=false`）**  
meeting-server 使用自有 `FeishuService` **直连** 飞书 Open API，与改造前一致。

**启用 Bot 中转（推荐生产统一审计时）**

1. 部署并启动 [feishu-scheduled-bot](../../feishu-scheduled-bot/docs/USER-MANUAL.md)（默认端口 **8764**，与 meeting **8765** 错开；生产可与 meeting 共库 `intelligence`）。
2. 两服务使用 **同一飞书应用** 凭证；Bot 配置 `FEISHU_API_KEY`。
3. meeting-server 环境变量示例：

```bash
MEETING_NOTIFY_BOT=true
MEETING_NOTIFY_BOT_URL=http://127.0.0.1:8764
MEETING_NOTIFY_BOT_API_KEY=<与 Bot 相同的 API Key>
MEETING_NOTIFY_FALLBACK=true
```

1. 会后通知写入 Bot 库表 `int_scheduled_push_log`，`trigger_type=EVENT`；运维可查：
  `GET http://<bot-host>:8764/api/logs?triggerType=EVENT`（需 `X-API-Key`）。

**仍由 meeting-server 直连飞书（不经 Bot）的能力**

- 飞书机器人 **命令回复**（开始/结束会议、查看纪要列表等）
- 会中 **交互卡片**（开始会议、结束处理中、会务入口等）
- **AI 主持 TTS**（WebSocket，非飞书 IM）
- 飞书 **文档** 创建与写入（`createDoc` / `updateDoc`）

**会中静音**  
AI 主持进行中，对群 `chat_id` 注册的静音会 **阻止** 会后类卡片发出（与直连时代行为一致）；判断在 meeting 侧完成，再决定是否调用 Bot。

**故障**  
Bot 不可用且 `fallback-direct=true` 时，自动回退 `FeishuService` 发送，避免用户收不到纪要链接。

---

## 11. 声纹注册

### 11.1 何时注册

建议在 **会前** 完成，以便纪要中显示真实姓名（需开启 `ISV_ENABLED` 及讯飞声纹配置）。

### 11.2 操作步骤

1. 在飞书发送：`**注册声纹 姓名:张三`**（姓名须与会议参会人一致）。
2. 机器人回复含 **H5 注册链接** 的卡片。
3. 在安静环境按页面提示朗读并完成注册。

若提示已注册，无需重复注册。

---

## 12. Web 页面与链接说明


| 路径                            | 用途          | Token         |
| ----------------------------- | ----------- | ------------- |
| `/start-meeting.html?token=…` | 飞书会务选会      | 飞书 Web 入口 JWT |
| `/rec/{meetingId}?token=…`    | 会议主页（操作员入口，三模块同屏） | 录音 JWT        |
| `/host/{meetingId}?token=…`   | 会议主页（主持入口，同 `host-meeting.html`） | 主持 JWT        |
| `/static/index.html`          | 旧版纯录音壳（遗留，非飞书主入口） | —               |
| `/join/{meetingId}?token=…`   | 线上到会确认      | 个人 JWT        |
| 声纹注册页                         | 由「注册声纹」指令下发 | 注册会话 token    |


链接均含短期 JWT，过期后需重新从飞书获取。

**缓存：** 前端静态资源带版本号；发版后若页面未更新，可调整 `MEETING_PAGE_CACHE_BUSTER` 并重新开会取链。

---

## 13. 常见问题

### Q1：线上同事打开了会议主页，为什么没记为已到？

会议主页是操作员/主持共用链接，无个人身份。必须使用 **个人入会链接** `/join/...`。

### Q2：线上同事没收到单聊卡片？

- 未设 `attendanceMode: "ONLINE"`；
- `userId` 不是 `open_id`；
- 用「开始会议 参会人:姓名」创建，仅有占位 ID。

处理：API 修正数据或使用 `participant-links` 人工发送。

### Q3：远程同事被点名「请答到」？

该人被登记为 **线下**，请改为 `ONLINE` 后重新开会或更新参会人。

### Q4：议题时间到了为什么没自动下一项？

当前设计为 **TTS 提醒 + 手动「下一议题」**；自动切题尚未默认开启。

### Q5：多人同时打开会议主页推流？

仅 **一个** WebSocket 音频连接有效，请只保留一台操作员设备。

### Q6：飞书链接打不开？

检查 `meeting.base-url` 是否为 **HTTPS**、证书是否有效、防火墙是否放行 8765（或反代端口）。

### Q7：纪要里说话人都是 Speaker 0？

请参会人提前 **注册声纹**，并确认 `ISV_ENABLED=true`；或会后使用「修改说话人」指令。

### Q8：会序 OpenClaw 通报没有出来？

1. 确认配置表该行 `openclaw_briefing=1` 且 `feishu_doc_url` 已填且有效。
2. 确认 `meeting.host.agenda-briefing.enabled=true` 且 OpenClaw（`OPENCLAW_AGENT_PROVIDER=mcp`）可用。
3. 查看会序模块「会序参考资料」区 `briefingStatus`：`loading` 等待生成，`failed` 看 `briefingError` 或服务器日志。

### Q9：纯线上会议可以吗？

可以。线上盘点结束后若无线下待点名人员，检点直接进入 **结束** 状态。

### Q10：开了 `MEETING_NOTIFY_BOT=true` 但没收到纪要卡片？

1. 确认 **feishu-scheduled-bot** 已启动且 `FEISHU_API_KEY` 与 meeting 的 `MEETING_NOTIFY_BOT_API_KEY` 一致。
2. 查 Bot：`GET /api/logs?triggerType=EVENT`，看是否 `FAILED` 或 `SKIPPED`（幂等 `DUPLICATE`）。
3. 若 Bot 宕机，确认 `MEETING_NOTIFY_FALLBACK=true` 时 meeting 日志是否有 “Falling back to direct Feishu”。
4. AI 主持会中可能对群 **静音**，会后推送会被抑制直至 unmute。

---

## 14. 快速检查清单

### 会务（会前）

- 生产库已执行必要 migration
- `BASE_URL` 为公网 HTTPS
- 混合会：线上人员 `ONLINE` + `open_id` + 姓名
- 飞书机器人已入群且可单聊
- 关键参会人已注册声纹（可选但推荐）

### 现场（会中）

- 已打开 **会议主页** 并点击 **开始会议**（录音推流 + 主持会话，单音频连接）
- 开场 / 检点正常
- 线上同事已在盘点窗口内打开 **个人链接**
- 线下同事知悉答到方式

### 会后

- 已在会议主页 **结束会议**
- 群内收到纪要卡片；`查看纪要` 可打开文档
- 待办责任人已在飞书收到任务（若启用）

---

## 15. 附录

### 15.1 会议状态（简要）


| 状态                  | 含义                      |
| ------------------- | ----------------------- |
| STARTED / RECORDING | 进行中、录音中                 |
| PROCESSING          | 纪要生成中                   |
| COMPLETED           | 已完成                     |
| 其他                  | PAUSED、CANCELLED 等见系统枚举 |


### 15.2 用户相关 API


| 方法     | 路径                                         | 说明              |
| ------ | ------------------------------------------ | --------------- |
| `POST` | `/api/v1/meetings`                         | 创建会议            |
| `POST` | `/api/v1/meetings/{id}/start`              | 启动会议            |
| `POST` | `/api/v1/meetings/{id}/end`                | 结束会议            |
| `POST` | `/api/v1/meetings/{id}/recording-session/end` | 会议主页结束会议（需 Bearer 录音 JWT） |
| `GET`  | `/api/v1/host/meetings/{id}/state`            | 主持模块状态 |
| `POST` | `/api/v1/host/meetings/{id}/start`            | 开始主持（常与录音同时由页面触发） |
| `POST` | `/api/v1/meetings/{id}/check-in`           | 线上到场（个人 JWT）    |
| `GET`  | `/api/v1/meetings/{id}/participant-links`  | 参会人及个人 joinUrl  |
| `GET`  | `/api/v1/meeting-type-presets`             | 会务类型列表          |
| `GET`  | `/api/v1/meetings/{id}/agenda-doc-content` | 会序资料正文          |


### 15.3 相关文档


| 文档                                                 | 说明         |
| -------------------------------------------------- | ---------- |
| [README.md](README.md)                             | 文档索引       |
| [PRD-Java-二期.md](PRD-Java-二期.md)                   | 二期需求       |
| [product/混合参会检点-用户手册.md](product/混合参会检点-用户手册.md)   | 混合检点专题     |
| [product/混合参会-音频与检点策略.md](product/混合参会-音频与检点策略.md) | 技术策略       |
| [product/AI会议主持人-开发文档.md](product/AI会议主持人-开发文档.md) | 主持能力开发说明   |
| [会中模块进度评估.md](会中模块进度评估.md)                         | 会中功能完成度与待办 |


---

*如有功能与本文不一致，以当前部署版本代码为准；欢迎将现场问题反馈给运维以便更新手册。*