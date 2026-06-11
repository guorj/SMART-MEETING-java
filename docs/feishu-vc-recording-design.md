# 飞书 VC 云端录制接入设计

> 文档版本：2026-06-10  
> 状态：**评估完成，待实施**  
> 关联：[开关手册.md](开关手册.md) §3.4、[USER-MANUAL.md](USER-MANUAL.md) §2.1  
> 前置问题：浏览器麦克风无法采集飞书会议中远程参会人的语音

---

## 1. 背景与问题

当前 meeting-server 的音频采集链路为：

```
浏览器 getUserMedia() → 本机麦克风 → WebSocket /ws/audio/{meetingId}
  → AudioCacheService.writeAudioChunk() → {cache-dir}/{date}/{meetingId}.pcm
  → (可选) AsrBridgeService → 讯飞实时 ASR
```

**核心问题**：浏览器 `getUserMedia()` 只能采集本机物理麦克风的输入。当用户同时参加飞书视频会议时：

- 飞书会议的语音走飞书客户端内部的 RTC 通道，浏览器无法访问
- `echoCancellation: true` 会主动消除扬声器回放的远程人声音
- 远程参会人的语音**不会**出现在浏览器录音文件中

即：当前系统只能录到"现场声音"，丢失了"飞书线上声音"。

---

## 2. 方案概述

引入飞书 VC 云端录制作为第二条音频源，与现有浏览器麦克风录音并行：

```
音频源 A（保留）：浏览器麦克风 → 现场声音 → {meetingId}.pcm
音频源 B（新增）：飞书 VC 云端录制 → 线上声音 → {meetingId}_vc.pcm
离线 ASR 根据场景选择最优源（或合并后）送入讯飞 IST
```

---

## 3. 录音文件分析

### 3.1 两份录音文件

| 文件 | 来源 | 存储路径 | 内容 |
|------|------|----------|------|
| **File A** | 浏览器麦克风 PCM | `{cache-dir}/{date}/{meetingId}.pcm` | 主持人/操作者自己的声音 + 同一物理房间内其他人的声音 |
| **File B** | 飞书 VC 云端录制 | `{cache-dir}/{date}/{meetingId}_vc.pcm` | 所有通过飞书会议接入的参会人混音 |

### 3.2 各文件覆盖范围

**File A — 浏览器麦克风**：
- 采集源：本机物理麦克风
- 覆盖：现场人（主持人 + 同房间其他人）
- 不覆盖：远程参会人（echoCancellation 会消除扬声器回放声）
- 简言之 ≈ **现场声音**

**File B — 飞书 VC 云端录制**：
- 采集源：飞书服务端对各入会设备音频流的混音
- 覆盖：所有入飞书会议的人
- 不覆盖：未入飞书的纯现场人
- 特殊情况：如果主持人也从飞书客户端入会 + 共享麦克风，则整个房间声音都进入飞书，File B **已包含全体**
- 简言之 ≈ **飞书线上声音**

### 3.3 混合会场景下 File B 的完整性

混合会中，如果主持人从飞书客户端入会且使用共享麦克风（现场所有人共用一个麦克风/会议话筒），飞书云端录制的混音构成为：

```
飞书服务端混音
├── 主持人设备麦克风流 → 共享麦克风 → 采集到 主持人 + 全体现场人
└── 远程参会人各自的设备麦克风流 → 采集到 远程参会人
= 全体参会人
```

**结论**：此场景下 File B 已包含所有人，可直接用于离线转写，无需与 File A 合并。

唯一的质量风险是物理收音条件（共享麦克风离某些现场人太远），需通过定向麦克风/会议话筒解决，属现场设备问题而非系统问题。

---

## 4. 混合会判断逻辑

采用两层判断，缺一不可：

| 层 | 判断条件 | 含义 |
|---|---------|------|
| 注册层 | `int_participant` 中同时存在 `ONLINE` 和 `OFFLINE` 的参会人 | 会议**预期**是混合会 |
| 运行层 | `vc.v1.meetingRecording.start` 调用成功，拿到 `recording_id` | 飞书 VC **实际**在运行并录制中 |

仅凭注册层判断不够——可能线上人实际没入会；仅凭运行层判断也不够——可能纯线上会碰巧有 offline 注册人。

---

## 5. 离线 ASR 音频源选择策略

| 场景 | 离线 ASR 用哪份 | 理由 |
|------|---------------|------|
| 纯现场会（无飞书 VC） | File A | 唯一音频源 |
| 纯线上会 | File B | 唯一完整音频源 |
| 混合会，主持人入飞书 + 共享麦克风 | **File B** | 已包含全体，直接用 |
| 混合会，主持人未入飞书 | File A + File B **合并** | File A 缺远程人，File B 缺现场人，都不完整 |

第四种场景（主持人未入飞书）是唯一需要合并的情况，实际会议中概率很低，但设计上必须覆盖。

合并方式：ffmpeg 将 File A 与 File B 混音为 `{meetingId}_merged.pcm`，送入离线 ASR 后可按保留策略清理。

### 选择策略流程

```
会议结束 → 检查 vc_recording_id 是否存在
  ├─ 无 → 使用 File A（纯现场会）
  └─ 有 → 下载飞书录制 → 转码为 File B
       → 检查是否有 OFFLINE 参会人
         ├─ 无 → 使用 File B（纯线上会）
         └─ 有 → 检查主持人是否入飞书（vc_participant_list 含主持人）
              ├─ 是 → 使用 File B（混合会，主持人入飞书）
              └─ 否 → ffmpeg 合并 A+B → 使用 merged（混合会，主持人未入飞书）
```

---

## 6. 详细设计

### 6.1 新增服务类

| 类 | 职责 |
|----|------|
| `FeishuVcRecordingService` | 调用 `vc.v1.meetingRecording.start/stop/get`；管理录制生命周期 |
| `FeishuVcRecordingCallbackHandler` | 处理 `vc.recording.completed_v1` 事件回调；下载录制文件 → ffmpeg 转 PCM → 写入 File B |
| `AudioSourceResolver` | 根据 §5 策略判断离线 ASR 应使用哪份音频文件 |

### 6.2 会议表新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `vc_meeting_id` | `VARCHAR(64)` | 飞书 VC 会议号（调用录制 API 时获得） |
| `vc_recording_id` | `VARCHAR(64)` | 录制 ID |
| `vc_recording_url` | `VARCHAR(512)` | 录制文件下载 URL（回调时获得） |
| `audio_source` | `VARCHAR(20)` | 离线 ASR 实际使用的音频来源：`browser` / `vc_recording` / `merged` |

### 6.3 录制文件下载与转码

```
vc.recording.completed_v1 回调
  → 从回调中提取 download_url
  → HTTP 下载录制文件（通常为 MP4/M4A）
  → ffmpeg -i input.mp4 -f s16le -acodec pcm_s16le -ar 16000 -ac 1 output.pcm
  → 写入 {cache-dir}/{date}/{meetingId}_vc.pcm
  → 更新 int_meeting.vc_recording_url / audio_source
```

### 6.4 与现有离线 ASR 链路的对接

当前链路：`RecordingService.stopRecording()` → `PostMeetingOrchestrator` → 离线 ASR 步骤 → 读取 `meeting.audio_path`（File A）→ `XfyunOfflineClient`

改造点：
- 离线 ASR 步骤执行前，调用 `AudioSourceResolver.resolve(meetingId)` 确定音频源
- 如果最优源为 File B 或 merged，等待飞书录制回调完成（超时兜底回退 File A）
- 将 `meeting.audio_path` 更新为选定的文件路径，后续逻辑不变

### 6.5 飞书 Webhook 事件路由

在 `FeishuWebhookController` 中新增 `vc.recording.completed_v1` 事件处理：

```java
} else if ("vc.recording.completed_v1".equals(eventType)) {
    JsonNode finalBody = body;
    CompletableFuture.runAsync(() -> handleVcRecordingCompleted(finalBody));
}
```

### 6.6 录制生命周期管理

| 时机 | 动作 |
|------|------|
| 建会启动 | 判断是否需要开启飞书录制（`vc.recording-enabled=true` 且有 ONLINE 参会人）→ 调用 `vc.v1.meetingRecording.start` |
| 会议结束 | 调用 `vc.v1.meetingRecording.stop`（若录制在进行中） |
| 录制完成回调 | 下载转码、写入 File B、触发离线 ASR 音频源选择 |

---

## 7. 需要申请的飞书权限

| 权限 | Scope | 用途 |
|------|-------|------|
| 视频会议录制 | `vc:recording` | 开启/停止/获取云端录制 |
| 视频会议信息 | `vc:meeting:read` | 获取会议号、参会人列表 |

事件订阅需新增：`vc.recording.completed_v1`

---

## 8. 开关设计

| 配置键 | 默认值 | 作用 |
|--------|--------|------|
| `meeting.vc.recording-enabled` | `false` | 飞书 VC 云端录制总开关 |
| `meeting.vc.auto-start-recording` | `true` | 建会时是否自动开启飞书录制（`recording-enabled=true` 时生效） |
| `meeting.vc.recording-callback-timeout-min` | `10` | 等待飞书录制回调的超时时间（分钟），超时后回退使用 File A |

---

## 9. 测试验收标准

1. **纯现场会**：不触发飞书录制，离线 ASR 使用 File A，结果与当前行为一致
2. **纯线上会**：飞书录制完成回调后，离线 ASR 使用 File B，转写包含全部线上参会人
3. **混合会（主持人入飞书）**：离线 ASR 使用 File B，转写包含现场+远程参会人
4. **混合会（主持人未入飞书）**：ffmpeg 合并 A+B，离线 ASR 使用 merged，转写包含全体
5. **录制回调超时**：超时后回退使用 File A，不阻塞纪要生成
6. **录制失败**：`vc.v1.meetingRecording.start` 返回错误时，仅使用 File A，不影响会议正常进行
7. **开关关闭**：`vc.recording-enabled=false` 时，不调用任何飞书 VC API，行为与当前完全一致

---

## 10. 与现有麦克风拾音的关系

浏览器麦克风 → WebSocket → PCM 缓存链路**完整保留**，不做任何改动。

两条音频源并行独立：
- File A 在会议进行中实时写入（不受飞书录制影响）
- File B 在飞书录制完成后异步写入（不影响会议中体验）
- 离线 ASR 根据策略选择最优源，用户无感

---

## 11. 实施优先级

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| P0 | 文档评估（本文档）| ✅ 已完成 |
| P1 | 飞书权限申请 + `vc.recording.completed_v1` 事件订阅 | 待启动 |
| P2 | `FeishuVcRecordingService` + 回调处理 + ffmpeg 转码 | 待启动 |
| P3 | `AudioSourceResolver` + 离线 ASR 链路对接 | 待启动 |
| P4 | 开关接入 + Admin 热更 + 生产验证 | 待启动 |
