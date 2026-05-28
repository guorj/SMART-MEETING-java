# 智能会议系统 — 用户手册

**文档版本：** 2026-05-27 · 与当前代码实现对齐（含 v0.18 三场景音频兜底）

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
| AI 主持 TTS、议题切换 | 可用 | 议题到时提醒后需手动「下一议题」 |
| 现场录音 + 实时 ASR | 可用 | 可关闭实时转写走离线 ASR |
| 会后纪要 + 待办提取 | 可用 | LLM 生成 + AI 增强（可选） |
| 会前事项对比通报 | 可用 | Bot 定时生成 + OpenClaw 主路径 |
| 建会待办进度卡片 | 已下线 (v0.9) | 由会前事项对比通报取代 |
| 三场景会议支持 (v0.18) | 可用 | OFFLINE/HYBRID/ONLINE 自动推导 + 云端录音 URL 兜底下载（纯线上/混合场景） |

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

---

## 4. 角色与职责

| 角色 | 主要操作 | 入口 |
|------|----------|------|
| **会务/发起人** | 创建会议、维护参会人、结束会议（备用） | 飞书机器人「会议管理」、dashboard 工作台 |
| **现场录音操作员** | 会议主页·录音模块：推流、结束会议 | 飞书卡片链接 `/rec` |
| **会议主持** | 会议主页·主持模块：开始、检点、下一议题 | `/host/{id}` |

---

## 5. 常见问题与故障排查

- **Q**：纯线上会议为什么没有本地录音文件？  
  **A**：纯线上场景默认不推流，依赖 `sourceAudioUrl` 兜底。创建时务必传入可访问的云端录音 URL。

- **Q**：云端下载失败怎么办？  
  **A**：检查日志中的 `MeetingAudioMaterializerService` 错误信息；确认 URL 可公开访问或携带有效 token；必要时手动上传本地 PCM 作为降级。

- **Q**：如何在 Admin UI 查看会议场景？  
  **A**：进入 `meeting-admin-server` → 「meetings」模块，详情页会展示 `meetingScenario` 与 `sourceAudioUrl` 字段。

---

## 6. 附录

### 6.1 常用 API 一览（三场景相关）

| 接口 | 方法 | 关键字段 | 说明 |
|------|------|----------|------|
| 创建会议 | POST `/api/v1/meetings` | `meetingScenario`, `sourceAudioUrl`, `participants[].attendanceMode` | 显式或自动推导场景 |
| 查询会议 | GET `/api/v1/meetings/{id}` | 返回 `meetingScenario`, `sourceAudioUrl` | 前端展示用 |
| 结束会议 | POST `/api/v1/meetings/{id}/end` | 触发云端兜底逻辑 | 无需额外参数 |

### 6.2 相关文档

- [meeting-admin.md](meeting-admin.md) — 管理后台使用说明
- [PRD-Java-二期.md](PRD-Java-二期.md) — 产品需求与架构主文档
- `schema-upgrade/v0.18-meeting-scenario-audio-fallback.sql` — DDL 升级脚本

---

*本手册随代码版本同步更新。v0.18 重点解决「纯线上/混合场景纪要生成」问题，后续迭代将补全飞书 VC 录制文件自动获取能力。*