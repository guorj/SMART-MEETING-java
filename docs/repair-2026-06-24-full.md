# smart-meeting-java 安全/UX 审计与修复方案（2026-06-24）

- **编制日期**：2026-06-24
- **范围**：`meeting-server`（含 `meeting-config-core`）后端鉴权/状态机 + 前端 `dashboard.html` / `host-meeting.html` / `recorder.js`
- **审计方式**：静态代码审查 + 交互流程推演（未做动态渗透）
- **当前状态**：审计完成；**代码基本未落地**（B2-01 / B3-01 / B3-03 有部分子项已落地，见 Part C 各条目状态）
- **原则**：最小 diff；安全 P0 与 UX P0 可并行两批 PR；每批合并前跑 `mvn test`

## 阅读说明

本文档由原三份文档合并：

| 原文件 | 本文位置 |
|--------|----------|
| `security-audit-2026-06-24.md` | [Part A · 安全审计](#part-a--安全审计) |
| `ux-audit-2026-06-24.md` | [Part B · 用户体验审计](#part-b--用户体验审计) |
| `repair-plan-2026-06-24.md` | [Part C · 修复执行方案](#part-c--修复执行方案) |

维护时只需更新本文件对应 Part 的「状态」列；发版记录仍写入 `版本迭代历史.md` §2。

## 审计 ID 总索引

| ID | 来源 | 严重级别 | 标题（简） | 修复批次 |
|----|------|----------|------------|----------|
| C1 | 安全 | Critical | 生产密钥硬编码进 git | B0, B1-01 |
| C2 | 安全 | Critical | 录音控制接口零鉴权 | B1-02 |
| C3 | 安全 | Critical | recording-url / host-url 零鉴权 | B1-02 |
| C4 | 安全 | Critical | 核心会议接口零鉴权 | B1-02 |
| C5 | 安全 | Critical | 内部会议管理接口零鉴权 | B1-02 |
| C6 | 安全 | Critical | 路径穿越任意文件读 | B1-03 |
| H1 | 安全 | High | 飞书 Webhook 验签默认放行 | B1-04 |
| H2 | 安全 | High | JWT 走 URL query 参数 | （后续加固） |
| H3 | 安全 | High | JWT 密钥弱默认值 | B1-01 |
| H4 | 安全 | High | 内部 token 弱保护 | B1-05 |
| H5 | 安全 | High | 状态机无 DB 原子性 | B3-02 |
| H6 | 安全 | High | Outbox 幂等键含 sentAt | B3-02 |
| H7 | 安全 | High | PROCESSING → RECORDING 回退 | B3-02 |
| H8 | 安全 | High | endMeeting 不拆 host/audio | B3-01 |
| H9 | 安全 | High | Host start 不持锁 + 泄漏 | B3-03 |
| H10 | 安全 | High | 结束后 PCM 仍追加 | B3-02 |
| M1 | 安全 | Medium | Webhook 无 event_id 去重 | B1-04 |
| M2 | 安全 | Medium | Voiceprint 页 XSS | B4-02 |
| M3 | 安全 | Medium | CORS 过宽 | B4-03 |
| M4 | 安全 | Medium | Todo assign/split 无鉴权 | B4-01 |
| M5 | 安全 | Medium | getMeetingMinute 鉴权可选 | B1-02 |
| M6 | 安全 | Medium | 草稿复用 TOCTOU | — |
| M7 | 安全 | Medium | AudioNormalize 无原子 rename | B4-08 |
| M8 | 安全 | Medium | listMeetings size 无上限 | B4-07 |
| M9 | 安全 | Medium | meetingId 拼路径无校验 | B1-03 |
| M10 | 安全 | Medium | 看门狗不覆盖 STARTED | — |
| L1–L4 | 安全 | Low | 见 Part A | B4-10 等 |
| UX-H1 | UX | High | 「恢复」弹窗被拦截 | B2-01 |
| UX-H2 | UX | High | 麦克风拒绝后会话仍 STARTED | B2-02 |
| UX-H3 | UX | High | Token 过期静默冻结 | B2-03 |
| UX-H4 | UX | High | 后退显示过期 dashboard | B2-04 |
| UX-H5 | UX | High | 预约按钮未禁用重复提交 | B2-04 |
| UX-M1–M11 | UX | Medium | 见 Part B | B2/B4 |
| UX-L1–L8 | UX | Low | 见 Part B | B4 |

---

## Part A · 安全审计
- **审计日期**：2026-06-24
- **审计范围**：`meeting-server`（含 `meeting-config-core`）鉴权、密钥、文件 I/O、状态机与并发
- **审计方式**：静态代码审查，未做动态渗透
- **状态**：审计完成，**未修复**（修复进度见本文 Part C）

---

## 严重程度图例

| 级别 | 含义 |
|------|------|
| 🔴 Critical | 可被互联网任意调用者利用，影响数据机密性/完整性/可用性 |
| 🟠 High | 在特定条件下可被利用，或导致关键业务异常 |
| 🟡 Medium | 需配合其他漏洞或非默认配置才可利用 |
| 🟢 Low | 加固建议，单独影响有限 |

---

## 🔴 Critical

### C1 · 生产密钥硬编码进 git

**位置**
- `meeting-server/src/main/resources/application-prod.yml:22-107`
- `meeting-server/src/main/resources/application-dev.yml:21-140`

**证据**

```yaml
# application-prod.yml
23:    url: jdbc:mysql://60.205.1.17:3306/intelligence?...useSSL=false...
25:    password: "intelligence@2026"
42:    app-secret: "su4Ld0hCNI7tJwRlWpVWnNBxfILBlftt"
59:      api-key: "cf2d80e0d6bddb829c43d147dfc1d664"
60:      api-secret: "NGFmOGU1MDRjNGQ3NTQwY2NkOTZmNTBl"
86:    api-key: "sk-bc91279066c0419f80df68b03e0d90d8"
107:    secret: "IQ7vgVe5uBy0XJ6q8mzrqHgRzl9md+1oO1qKLHnxchxdUyhN2qa3aRqiCK+99U5K"
```

dev 与 prod 指向同一公网 MySQL `60.205.1.17`，且 `useSSL=false`。

**影响**

任意获得仓库读权限者可：
- 直连数据库读写会议/参会人/纪要
- 冒充飞书应用读取租户消息、推送任意卡片
- 烧光讯飞 ASR 配额、DeepSeek 余额
- 伪造任意 JWT（见 H3）

**修复建议**
1. **立即轮换**上述全部凭据
2. YAML 仅保留 `${ENV_VAR:}` 占位，禁止字面值
3. MySQL 限定内网/VPN，启用 TLS
4. `.gitignore` 加入 `application-prod.yml`，历史凭据视为已泄露

---

### C2 · 录音控制接口零鉴权

**位置**：`meeting-server/src/main/java/com/smartmeeting/api/controller/AudioController.java:33-96`

```java
33:    @PostMapping("/{meetingId}/start")
34:    public ApiResponse<String> startRecording(@PathVariable String meetingId) { ... }
75:    @PostMapping("/{meetingId}/stop")
76:    public ApiResponse<Map<String, Object>> stopRecording(@PathVariable String meetingId) { ... }
```

`start/pause/resume/stop/status` 均无 JWT、无内部 token、无 creator 校验。`stop` 触发整条纪要/待办生成链。

**利用场景**：`POST /api/v1/audio/{anyMeetingId}/stop` 结束任意进行中会议并启动离线 ASR。

**修复建议**：所有方法要求 host-operator JWT 或内部 token。

---

### C3 · `/recording-url`、`/host-url` 零鉴权签发操作员 JWT

**位置**：`meeting-server/src/main/java/com/smartmeeting/api/controller/RecordingController.java:40-88`

```java
40:    @GetMapping("/{id}/recording-url")
41:    public ApiResponse<Map<String, Object>> getRecordingUrl(@PathVariable String id) {
48:        String token = jwtUtil.generateOperatorMeetingToken(id, JwtUtil.TYPE_RECORDING);
73:    @GetMapping("/{id}/host-url")
79:        String token = jwtUtil.generateOperatorMeetingToken(id, JwtUtil.TYPE_HOST);
```

知道任意 meetingId 即可换取 4 小时有效的 operator JWT（含 `can_push_audio=true`），随后可连 `/ws/audio` 推 PCM 或驱动 `MeetingHostController`。

**利用场景**：攻击者猜测/泄露会议 UUID → 调用本接口 → 接管主持会话。

**修复建议**：要求 dashboard JWT + 创建人/主持人权限，或要求内部 token。

---

### C4 · 核心会议接口零鉴权

**位置**：`meeting-server/src/main/java/com/smartmeeting/api/controller/MeetingController.java:80-133`

```java
80:    @PostMapping                       // createMeeting
109:   @PostMapping("/{id}/start")        // startMeeting
120:   @PostMapping("/{id}/end")          // endMeeting
128:   @PatchMapping("/{id}/schedule")    // reschedule
```

无 token 校验。`createMeeting` 允许请求体自填 `creatorId`；`listMeetings` 不强制 creator 过滤；`getMeetingMinute:330-339` 鉴权可选，无 header 时直接返回纪要。

**修复建议**：要求 dashboard JWT + 对应 grant；`creatorId` 强制取自 token claim。

---

### C5 · 内部会议管理接口零鉴权

**位置**：`meeting-server/src/main/java/com/smartmeeting/api/controller/InternalMeetingController.java:26-60`

```java
26:    @PutMapping("/{meetingId}")
40:    @PostMapping("/{meetingId}/participants")
47:    @PutMapping("/{meetingId}/participants/{participantId}")
55:    @DeleteMapping("/{meetingId}/participants/{participantId}")
```

同包 `InternalMeetingAdminController` 每个方法都调 `internalApiAuth.requireToken`，本控制器完全不鉴权。

**修复建议**：同 `InternalMeetingAdminController`，加 `requireToken` + IP 白名单。

---

### C6 · 路径穿越任意文件读

**位置**
- 汇点：`meeting-config-core/src/main/java/com/smartmeeting/config/agenda/AgendaMaterialDiskStorage.java:53-73`
- 入口：`meeting-server/src/main/java/com/smartmeeting/api/controller/MeetingController.java:409-428`（`proxyFileImage`）

```java
// AgendaMaterialDiskStorage.java
53:    Path metaPath = storageDir.resolve(fileId.trim() + ".meta.json");
73:    Path path = storageDir.resolve(fileId.trim() + ext);
```

`fileId` 直接拼接，无 `normalize()` + `startsWith` 校验。`proxyFileImage` 还缺少其他代理接口用的 `assertMeetingHasLocalFile`。

**利用场景**：

```
GET /api/v1/meetings/{anyMeetingId}/agenda-materials/proxy-file-image?fileId=../../../../etc/passwd&token=<jwt>
```

**对比正确写法**：`TodoAttachmentService.resolveStoragePath:118-125` 已做 `normalize()+startsWith` 校验，可作为模板。

**修复建议**：`fileId` 校验 `^[A-Za-z0-9-]+$`；所有 loadMeta/resolveDataPath 加 containment check；`proxyFileImage` 加 `assertMeetingHasLocalFile`。

---

## 🟠 High

### H1 · 飞书 Webhook 验签默认放行

**位置**：`meeting-server/src/main/java/com/smartmeeting/api/controller/FeishuWebhookController.java:423-428, 366, 451`

```java
423:    if (feishuVerificationToken == null || feishuVerificationToken.isBlank()) {
424:        return true;   // ← 默认放行
425:    }
```

- `verification-token` 默认空（`application.yml:343`），空时直接放行
- schema 2.0 flat 路径（`handleSchema20FlatCardCallback:366`）和 `trigger_v1` 路径（`:451`）**从不**校验
- `/webhook` 事件路径（`:99`）从不校验
- 未读取 `X-Lark-Signature`

**利用场景**：伪造 `card.action.trigger`，`operator.open_id` 设为受害者，value 选「开始/结束会议」即可执行。

**修复建议**：fail-closed；所有 webhook 路径校验 token + `X-Lark-Signature` SHA256 HMAC。

---

### H2 · JWT 走 URL query 参数

**位置**
- `DashboardController.java:47,58,66,75,82,89,96,103,112,120,129,137,146,156`
- `TodoController.java:35,42,51`
- `MeetingController.java:194,231,388,414,438`
- `AudioWebSocketHandler.java:390-399`、`MeetingHostWebSocketHandler.java:166-177`

所有 dashboard/todo/下载接口以 `?token=...` 传 JWT。token 会被：
- 反向代理/负载均衡 access log 留存
- Referer 头泄露给页面加载的第三方资源
- 浏览器历史记录

dashboard token 有效期 8 小时（`feishu-web-dashboard-expire-hours: 8`）。

**修复建议**：改用 `Authorization: Bearer`；WS 用一次性 ticket 换 cookie 或首帧鉴权。

---

### H3 · JWT 密钥弱默认值

**位置**
- `meeting-server/src/main/java/com/smartmeeting/util/JwtUtil.java:61`
- `meeting-server/src/main/resources/application.yml:365`

```java
61:    @Value("${meeting.jwt.secret:change-me-in-production}")
62:    private String secret;
```

```yaml
365:    secret: ${JWT_SECRET:this-is-…mum-key-change-in-production}
```

默认串已公开提交，启动时不校验是否仍是默认。配合 C3 可伪造任意 meeting 的 operator JWT。

**修复建议**：移除默认；启动时 fail-fast 校验长度与是否等于已知默认值。

---

### H4 · 内部 token 弱保护

**位置**：`InternalApiAuth.java:24`、`application.yml:305-307`

```java
24:    if (!expected.equals(request.getHeader("X-Internal-Token"))) { ... }
```

- 无 IP 白名单
- `equals` 非恒定时间（侧信道）
- 默认 token `dev-internal-reload` 已提交
- `reload-enabled` 默认 true

**修复建议**：IP 白名单 + `MessageDigest.isEqual` + 强随机 token + 默认关闭。

---

### H5 · 状态机无 DB 原子性

**位置**：`meeting-server/src/main/java/com/smartmeeting/statemachine/MeetingStateMachineService.java:27-58`

`apply` 是 select → reset SM → send event → updateById，无 `@Version`、无 `SELECT FOR UPDATE`、无按会议锁。`int_meeting` 表无版本列（`schema.sql:14-44`）。

**利用场景**：工作台「结束」与飞书「结束会议」并发 → 双方都过状态校验 → 双 dispatch 离线 ASR。

**修复建议**：改条件更新 `UPDATE int_meeting SET status='PROCESSING' WHERE id=? AND status IN ('RECORDING','PAUSED','STARTED')`，按影响行数决定是否 dispatch。

---

### H6 · Outbox 幂等键含 sentAt，去重失效

**位置**：`PostMeetingOrchestrator.java:53`、`MeetingDomainEventOutboxListener.java:33,50`

```java
// event_key = "offline-asr:" + meetingId + ":" + sentAt
```

`sentAt = System.currentTimeMillis()` 每次 dispatch 不同 → 并发 dispatch 产生不同 key → 两行 outbox → ASR 跑两遍（双倍讯飞费用、重复转写段）。

**修复建议**：key 改为 `"offline-asr:" + meetingId`（不含 sentAt），靠 `uk_event_outbox_key` 去重。

---

### H7 · PROCESSING → RECORDING 回退

**位置**：`RecordingService.java:309-332`（`attachCachedAudioRecording`）

`endMeeting` 已置 PROCESSING 后调用 `resolveAndPersistAudioPath`，内部可能 `apply(START_RECORDING)` 并 `putIfAbsent(RECORDING)`，使「已结束」会议重新可被录音/写 PCM。

**修复建议**：`resolveAndPersistAudioPath` 改为只读；任何状态写操作前先判断是否终态。

---

### H8 · `endMeeting` 不拆 host runtime / audio WS

**位置**：`MeetingService.endMeeting:250-283`

仅 `clearRecordingState`，从不调 `MeetingHostMediaTeardownService.beforeRecordingSessionEnd`。与 `endFromRecordingPage` 路径不一致。看门狗 `RecordingTimeoutChecker` 走此路径，留下 host tick 与 audio WS 永久运行。

**修复建议**：所有结束路径统一走 `endFromRecordingPage`，或 `endMeeting` 内部调用 teardown。

---

### H9 · `MeetingHostSessionService.start` 不持锁 + 调度泄漏

**位置**：`MeetingHostSessionService.java:148-150, 246-341`

`start` 未 `synchronized(lockFor(meetingId))`，两并发 `start` 都过 `containsKey` 检查，败者的 `ScheduledFuture` 永不取消，秒级 tick 永久泄漏。

**修复建议**：`start` 整体加 `synchronized(lockFor(meetingId))`；`runtimes.putIfAbsent` 后若返回非空则取消刚调度的 tick。

---

### H10 · 结束后 PCM 仍追加

**位置**：`AudioCacheService.java:70-78`、`AudioWebSocketHandler.java:166-168`

`writeAudioChunk` 不检查会议状态；WS `handleMessage` 见 `getRecordingState==null` 反而 `tryStartRecording` 重新激活录音态，PCM 持续增长。`f19cef17...` 即此现象。

**修复建议**：`writeAudioChunk` 与 `sendAudioFrame` 短路终态会议；WS 在会议结束后关闭会话而非自动重启录音。

---

## 🟡 Medium

| # | 位置 | 问题 |
|---|------|------|
| M1 | `FeishuWebhookController.java:145` | 无 `event_id` 去重，飞书重投可双创建/双结束 |
| M2 | `VoiceprintRegisterController.java:177,196-198,265` | `userName`/`token` 未转义拼进 HTML → 存储型 XSS |
| M3 | `WebConfig.java:27-31`、`WebSocketConfig.java:41,43` | CORS `*` + `allowCredentials(true)`；WS `setAllowedOrigins("*")` |
| M4 | `TodoController.java:57-72` | `assign`/`split` 待办无鉴权 |
| M5 | `MeetingController.java:330-339` | `getMeetingMinute` 鉴权可选，无 header 时直接返回纪要 |
| M6 | `FeishuMeetingStartCoordinator.java:64-75` | 草稿复用 TOCTOU，并发可建多份草稿 |
| M7 | `AudioNormalizeService.java:37-142` | 并发 normalize 写同一输出路径无原子 rename，可能产生混合 PCM |
| M8 | `MeetingService.java:318` | `.last("LIMIT "+(page*size)+","+size)`，size 无上限，int 溢出风险 |
| M9 | `AudioCacheService.java:45-47` | `meetingId` 拼路径无校验（配合 H3 可升级为 High） |
| M10 | `RecordingTimeoutChecker.java:50-55` | 看门狗只覆盖 RECORDING/PAUSED，STARTED 永不回收 |

---

## 🟢 Low

| # | 位置 | 问题 |
|---|------|------|
| L1 | `FeishuService.java:835,917,1104,1152,1914` | 飞书 API URL 路径段未 encode（host 固定，影响有限） |
| L2 | `FeishuWebhookController.java:102-105` | webhook body 在 INFO/debug 日志预览，PII 扩散 |
| L3 | `MeetingStateMachineService.java:60-65` | `forceStatus` 绕过状态机，`MinuteGenerationService.java:205` 用其强写 COMPLETED |
| L4 | `MeetingRecordingSessionEndService.java:55-64` | 处理中卡片在状态转换前发送，失败时用户已被通知 |

---

## 根因归纳

1. **无全局安全过滤链**：项目未启用 Spring Security，无 `SecurityFilterChain`/`@PreAuthorize`，鉴权完全靠控制器内手动 `jwtUtil.verify*`，遗漏即裸奔。
2. **三套结束路径不一致**：`endFromRecordingPage`（含 teardown）→ `endMeeting`（不 teardown）→ `updateStatus`/`forceStatus`（不校验），状态机、host runtime、audio WS 在不同路径下行为不同。
3. **状态机仅校验合法性、不保证原子性**：读改写无锁无版本号，是并发竞态的总根源。
4. **Outbox 幂等键设计错误**：含 `sentAt` 使其失去幂等性。
5. **密钥与代码同仓**：prod 配置直接提交真实凭据。

---

## 修复优先级建议

| 优先级 | 项 | 说明 |
|--------|-----|------|
| **P0 立即** | C1 凭据轮换与外迁、C2-C5 关键接口加鉴权、C6 路径穿越、H1 webhook 验签 fail-closed | 改动量可控、风险最高 |
| **P1 本周** | H3 JWT 启动校验、H5-H7 状态机原子化与 end 路径统一、H8-H10 host/audio 资源回收 | 涉及状态机重构，需测试 |
| **P2** | M1-M10 业务逻辑与输入校验 | |
| **P3** | L1-L4 加固 | |

---

## 参考：已确认安全的部分

- `FfmpegAudioConverter.java:46-83`：`ProcessBuilder(List)` 无 shell 注入
- `FeishuWebhookController`：JSON 解析（Jackson），无 XXE
- `dashboard.html` `esc()`：XSS 转义到位
- `host-meeting.html` `DOMPurify.sanitize`：富文本转写安全
- `XfyunOfflineClient` `BASE_URL`：硬编码，无 SSRF
- `TodoAttachmentService.resolveStoragePath:118-125`：路径穿越防护正确，可作为修复模板
- `FeishuRasterCache.sanitizeKey:28-33`：路径键清洗正确
- MyBatis-Plus `LambdaQueryWrapper`：参数化查询，无 SQLi（除 M8 的 `.last`）
- 无 `ObjectInputStream` 反序列化

---

*本报告由静态审计生成，未做动态验证。落地修复前建议在测试环境复现关键漏洞。*

---

## Part B · 用户体验审计
- **审计日期**：2026-06-24
- **审计范围**：`meeting-server/src/main/resources/static/{dashboard.html, host-meeting.html, recorder.js, meeting-context.js}` 前端交互
- **审计方式**：静态代码审查 + 交互流程推演
- **状态**：审计完成，**未修复**（修复进度见本文 Part C）

---

## 严重程度图例

| 级别 | 含义 |
|------|------|
| 🔴 High | 直接影响核心功能可用性或导致用户操作无效/数据丢失 |
| 🟡 Medium | 体验明显受损但可用，或影响特定场景/设备 |
| 🟢 Low | 体验瑕疵，不影响主流程 |

---

## 🔴 High

### H1 · 弹窗拦截器静默吞掉「恢复」会议窗口

**位置**：`host-meeting.html:433-435`（dashboard.html:422-443 同源）

```javascript
433:    const r=await api('/api/v1/dashboard/active-meeting/recover',{method:'POST'});
434:    if(r&&r.recordingUrl) window.open(r.recordingUrl,'_blank');
```

**用户体验**：用户点「恢复」→ `await api(...)` 先执行 → `window.open` 在 async 续段里调用，浏览器视为非用户手势，**弹窗被拦截**。API 已成功（服务端状态变更）但无窗口打开、无错误提示。用户重复点击，可能触发重复状态。

**修复建议**：在点击处理器里同步 `window.open` 一个空白窗口，await 后再 `location.href` 赋值；或检查 `window.open` 返回值，null 时降级为可点击链接（参考 `doCreateMeeting:592-595`）。

---

### H2 · 麦克风拒绝后服务端会议仍「已启动」

**位置**：`host-meeting.html:4193-4227`、`recorder.js:68-87`

```javascript
// recorder.js
68:    async start() {
69:        try {
70:            this.mediaStream = await navigator.mediaDevices.getUserMedia({ ... });
71:        } catch (err) {
72:            if (err.name === 'NotAllowedError') { this.onError('麦克风权限被拒绝...'); }
76:            return;   // 正常返回，不 reject
```

**用户体验**：`startMeetingFlow` 先 `beginHostSession()`（POST /start）**再** `connectRecorder()`。用户拒绝麦克风 → `recorder.start()` 不抛错 → `connectRecorder` 抛「录音未能启动」→ catch 重置本地标志，但**从不调 API 取消服务端已启动的会话**。服务端现在认为会议 STARTED 但无活跃录音器。重试时 `beginHostSession` 命中「已启动」会话被拒绝，错误信息混乱。具体「麦克风权限被拒绝」提示被通用「会议启动失败：录音未能启动…」覆盖（`showErr` 调了两次）。

**修复建议**：
- catch 中若 `hostSessionStarted` 已置但录音未附加，调 `/end` 或专用的 `/cancel-host-session` 清理
- `recorder.start()` 在 getUserMedia 失败时 reject 而非 return，让 catch 能区分麦克风拒绝与其他错误
- 保留更具体的错误信息

---

### H3 · Token 过期后主持页静默冻结

**位置**：`host-meeting.html:3986-3999, 4001-4007, 4161-4168`

```javascript
3986:    async function refreshHostStateFromApi() {
3992:        } catch (e) { console.warn('refreshHostStateFromApi', e); }   // 吞掉
```

**用户体验**：JWT 过期后 `hostApi`/`refreshHostStateFromApi` 开始返回 401。`refreshHostStateFromApi` 用 `console.warn` 吞错——1 秒轮询（`startHostStatePoll`）每秒打一次 401，计时器冻结、议程不更新，用户看到一个看似正常实则断连的页面。无 401 检测，无「令牌已过期，请在飞书重新发送「会议管理」」提示。dashboard 把 8 小时提示藏在折叠的 `<details>` 里（`dashboard.html:542-544`）。

**修复建议**：`hostApi`/`refreshHostStateFromApi` 检测 401，停止轮询，显示醒目 banner「登录已过期，请在飞书重新发送「会议管理」以获取新链接」。

---

### H4 · 浏览器后退/前进显示过期 dashboard

**位置**：`dashboard.html:655`

```javascript
655:    init();
```

**用户体验**：`init()` 仅在脚本解析时跑一次，无 `pageshow`/`popstate` 监听。用户从 dashboard → host-meeting → 按浏览器后退，页面从 bfcache 恢复，渲染的是**之前的**会议列表；`init()` 不会重跑，刚结束的会议仍显示「进行中」。

**修复建议**：`window.addEventListener('pageshow', e => { if (e.persisted) init(); })`，并在 `popstate` 时重取。

---

### H5 · 「预约会议」按钮提交期间未禁用 → 重复预约

**位置**：`dashboard.html:607-629`（todo 按钮 `259-297` 同病）

```javascript
607:    async function doScheduleMeeting(){
612:      msgEl.textContent='正在预约…';
613:      try{
614:        const r=await api('/api/v1/dashboard/schedule-meeting',{method:'POST',...});
```

**用户体验**：显示「正在预约…」时按钮仍可点。慢网下双击会创建两个预约会议。todo 按钮（`.btn-todo-complete`/`.btn-todo-block`/`.btn-todo-progress`/`.btn-todo-upload`）同样在异步期间不禁用。

**修复建议**：handler 开始即 disable，`finally` 恢复。`bindDetectedMeetingButtons:427,453` 已是正确范例。

---

## 🟡 Medium

### M1 · 议程预填失败后误导性「就绪」开始按钮

**位置**：`host-meeting.html:3928-3984, 4105-4126`

初始会议 fetch 失败时 catch 仅 `console.warn`。`meetingCancelled`/`meetingTerminal`/`meetingServerActive` 保持 false，`.finally` 启用「开始会议」。用户点击 → `beginHostSession` 可能失败或启动一个议程/预设从未加载的会议（`plannedTopicsCache` 为空）。

**修复建议**：跟踪 `prefillFailed`；`.finally` 中若预填失败且非 autostart，保持按钮禁用并显示「加载失败，请刷新」。

---

### M2 · Host WebSocket 重连无退避、无用户提示

**位置**：`host-meeting.html:4141-4158`

```javascript
4157:    sock.onclose = function () { setTimeout(connectHostWs, 2000); };
```

WS 断开后每 2s 永久重试（无指数退避， hammered down 服务器），无 UI 提示。用户看到冻结的计时器/转写，无「连接已断开，正在重连…」banner。对比 `recorder.js:290-306` 有指数退避 + 最大次数。

**修复建议**：指数退避（上限 ~30s），关闭时显示非阻塞「正在重连…」banner，`onopen` 清除。WS 健康时可停 1s 轮询。

---

### M3 · `init()` 重取竞态可让旧数据覆盖新数据

**位置**：`dashboard.html:472-579`

`init()` 在变更后通过 `setTimeout(init, …)` 递归调用（263, 271, 462, 599, 624, 641 行）。无在途守卫。两次 `init()` 重叠时，后解析完的写入 `#app.innerHTML`。网络抖动可能让旧调用最后完成，渲染过期列表，视觉上撤销用户刚完成的操作。每次 `init()` 还全量替换 `#app.innerHTML`，造成可见闪烁、丢失滚动/焦点。

**修复建议**：引入 `initSeq` token，每次 `init()` 自增，await 后若 token 变化则 bail。更好：只重渲染受影响面板。

---

### M4 · AudioContext 从未 resume，iOS Safari 录音失效

**位置**：`recorder.js:89-94`、`host-meeting.html:325-347`

```javascript
89:        try {
90:            this.audioContext = new AudioContext({ sampleRate: 16000 });
91:        } catch (e) {
92:            this.audioContext = new AudioContext();
93:        }
```

iOS Safari 启动 AudioContext 为 `suspended`，需在用户手势内 `audioContext.resume()`。代码从不调用。worklet/ScriptProcessor 可能永不发声，会议「开始」但无音频到服务端——用户看到「录音中」但计时器冻结、转写为空。`iosNoticeHost` banner（41 行）只说「保持屏幕常亮」。

**修复建议**：创建 AudioContext 后 `await this.audioContext.resume()`；若 `state === 'suspended'` 显示一次性 iOS 专属提示。

---

### M5 · HTTP 下麦克风错误信息无指导

**位置**：`recorder.js:78-87`、`host-meeting.html:41`

`getUserMedia` 在 HTTP（除 localhost）不可用。错误路径显示「麦克风访问失败: …」通用信息；`iosNoticeHost` 不提 HTTPS 要求。用户通过错误配置的 HTTP 反代访问，看到无 remediation 的晦涩错误。

**修复建议**：检测 `location.protocol !== 'https:' && hostname !== 'localhost' && hostname !== '127.0.0.1'`，在调 getUserMedia 前显示「麦克风必须在 HTTPS 下工作，请联系管理员」。

---

### M6 · Dashboard 无 `aria-live`，状态变化对屏幕阅读器静默

**位置**：`dashboard.html`（grep `aria-live`/`role=` 无匹配）

所有状态反馈（会议已创建/已结束/错误/todo 完成）渲染进 `.msg` 元素，无 `aria-live`。屏幕阅读器用户听不到「会议已创建」或「加载失败」。配合 M3（全量 `innerHTML` 替换），焦点丢失，用户不知发生了什么。

**修复建议**：`#create-msg`/`#schedule-msg`/todo `.todo-msg` 加 `aria-live="polite"`；全局错误容器加 `role="alert"`。

---

### M7 · Host 错误 banner 无 `aria-live`

**位置**：`host-meeting.html:43`

```html
43:    <div class="err ui-banner-err" id="err" style="display:none"></div>
```

`showErr(msg)`（545 行）写入 `#err`，无 `aria-live`。「录音重连失败」「会议启动失败」「主持会话已中断」等关键错误不向辅助技术播报。toast 栈（192 行）有 `aria-live="polite"`，但 `showErr` 是主错误通道。

**修复建议**：`#err` 加 `aria-live="assertive"` 和 `role="alert"`。

---

### M8 · 预约表单不校验过去时间

**位置**：`dashboard.html:607-629`

`doScheduleMeeting` 只检查 `timeEl.value` 非空。用户可选过去时间提交，客户端发送，后端返 400，用户看到原始错误。`doRescheduleMeeting:631-645` 用 `prompt()` 接受任意字符串，同样不校验。

**修复建议**：客户端比较 `scheduledTime` 与 `Date.now()`，先显示「计划时间必须晚于当前时间」再调 API。

---

### M9 · Autostart 弹麦克风提示无用户引导

**位置**：`host-meeting.html:4118-4126, 4185-4228`

带 `autostart=1`（dashboard `appendAutostartParam` 设置）时，页面打开抽屉并立即 `startMeetingFlow` → `beginHostSession` → `connectRecorder` → `getUserMedia`。浏览器弹麦克风权限对话框，无任何解释。用户点了飞书「开始会议」卡片，没料到麦克风提示，可能拒绝 → 触发 H2（服务端已启动，客户端放弃）。

**修复建议**：autostart 调 `connectRecorder` 前显示简短内联说明（「即将开始会议并请求麦克风权限，请点击「允许」」），要求确认点击，或至少在提示前显示 banner。

---

### M10 · `api()` 把原始 HTML/文本作为错误信息

**位置**：`dashboard.html:44-55`

```javascript
50:    if(!res.ok){const t=await res.text();throw new Error(t||'请求失败');}
```

反代 500 返回 HTML 错误页时，`e.message` 是整段 HTML，经 `加载失败: '+esc(e.message)`（575 行）渲染——转义了不是 XSS，但用户看到一大坨 HTML。网络失败抛 `TypeError: Failed to fetch` → 「加载失败: Failed to fetch」。`hostApi:4161-4168` 较好（用 `j.message || res.statusText`）。

**修复建议**：`api()` 中若 body 非 JSON，抛 `new Error('请求失败 ('+res.status+')')`；网络错误抛「网络连接失败，请检查网络」。

---

### M11 · 待开始会议「恢复」未先校验服务端状态即开窗

**位置**：`dashboard.html:422-443`

`isNotStarted(status)` 分支同步 `window.open(appendAutostartParam(recordingUrl), '_blank')`，不先验证会议在服务端仍是该状态。若另一操作员已开始/取消，打开的主持页显示过期状态。打开的页面再触发 M9（autostart 麦克风提示）。

**修复建议**：开窗前重新拉取会议状态，或传新鲜 token/nonce 让主持页加载时校验。

---

## 🟢 Low

| # | 位置 | 问题 |
|---|------|------|
| L1 | `dashboard.html:543` | 帮助文本硬编码「8 小时」token 有效期，与服务端配置可能不一致；藏在折叠 details 里 |
| L2 | `host-meeting.html:3967-3978` | 议程预设 fetch 失败仅 console.warn，用户看到「暂无议程」无法区分配置缺失 vs 网络失败 |
| L3 | `recorder.js:31-32, 290-306` | 录音 WS 重连 5 次后放弃，仅小 banner 提示「重连录音」，用户交谈中可能注意不到，音频静默丢失 |
| L4 | `host-meeting.html:3308-3313` | `parseHostAgendaItemsFromApi` 吞 JSON 解析错误，管理员无法诊断 |
| L5 | `dashboard.html:267, 632` | 改期/阻塞原因用原生 `prompt()`，与 UI kit 风格不符，部分浏览器在失焦时抑制，移动端体验差 |
| L6 | `host-meeting.html:3481-3488` | `clearAutostartQueryParam` 吞错，sandboxed iframe 中刷新会重新触发 autostart + 麦克风提示 |
| L7 | `dashboard.html:85-102` vs `host-meeting.html:4030-4035` | 状态徽章 class 词汇不统一（`badge-*` vs `ui-badge-*`），同状态跨页样式微妙不同 |
| L8 | `dashboard.html:298-300` | todo `<details>` 每次展开都重新拉取详情，慢网下反复闪烁「加载中…」 |

---

## 修复优先级建议

| 优先级 | 项 | 说明 |
|--------|-----|------|
| **P0** | H1-H5 | 直接导致用户操作无效或状态错乱，改动量小 |
| **P1** | M1-M5, M9-M11 | 影响特定场景/设备，需小幅重构 |
| **P2** | M6-M8, L1-L8 | a11y 与体验打磨 |

---

*本报告由静态审查 + 交互推演生成。落地前建议在真实设备（尤其 iOS Safari）上复现关键问题。*

---

## Part C · 修复执行方案

- **编制日期**：2026-06-24
- **原则**：最小 diff；安全 P0 与 UX P0 可并行两批 PR；每批合并前跑 `mvn test`

## 批次总览

| 批次 | 主题 | 预估改动面 | 是否阻塞发版 |
|------|------|------------|--------------|
| **B0** | 凭据轮换（运维，无代码） | 环境变量 / 飞书 / 讯飞 / DB | 是 |
| **B1** | 安全 P0：裸奔 API + 路径穿越 + Webhook | Java 控制器 + config | 是 |
| **B2** | UX P0：开始/结束/恢复主路径 | static HTML/JS | 是 |
| **B3** | 业务逻辑 P1：结束路径统一 + 状态机原子化 | Service 层 | 建议 |
| **B4** | 安全/UX P1–P2 | 分散 | 否 |

---

## B0 · 凭据轮换（运维，优先于一切代码修复）

> 审计项 **C1**。仓库内 prod/dev YAML 已含真实密钥，**视为已泄露**，必须先轮换再发版。

| 步骤 | 动作 | 负责人 |
|------|------|--------|
| 1 | MySQL `intelligence` 改密；限制 `60.205.1.17:3306` 仅内网/VPN；启用 TLS | 运维 |
| 2 | 飞书应用 `app-secret`、`verification-token`、`encrypt-key` 在开放平台重置 | 运维 |
| 3 | 讯飞 ASR/TTS、DeepSeek `api-key` 重置 | 运维 |
| 4 | 生成新 `JWT_SECRET`（≥32 字节随机）、新 `INTERNAL_RELOAD_TOKEN` | 运维 |
| 5 | 生产/开发环境改为 **仅环境变量**，YAML 删除字面值（见 B1-01） | 开发 |

**验收**：`application-prod.yml` / `application-dev.yml` 中无 `password:`、`secret:`、`api-key:` 字面值；服务用新凭据正常启动。

---

## B1 · 安全 P0

### B1-01 · YAML 密钥外迁 + JWT 启动校验

| 字段 | 状态 |
|------|------|
| 审计项 | C1, H3 |
| 状态 | ⬜ 待做 |

**改动**

1. `application-prod.yml`、`application-dev.yml`：所有密钥改为 `${ENV_VAR}`，删除字面值；prod DB URL 改内网地址占位。
2. 新增 `JwtSecretValidator`（或 `@PostConstruct` in `JwtUtil`）：启动时若 `meeting.jwt.secret` 等于已知默认串或长度 &lt; 32 → **fail-fast**。
3. `application.yml`：`JWT_SECRET` 默认值改为空（无 fallback 弱串）。

**文件**：`application*.yml`、`JwtUtil.java`、可选 `MeetingServerApplication` 旁配置类。

---

### B1-02 · 录音/会议/内部接口加鉴权

| 字段 | 状态 |
|------|------|
| 审计项 | C2, C3, C4, C5, M5 |
| 状态 | ⬜ 待做 |

**改动**

| 控制器 | 方法 | 鉴权方式 |
|--------|------|----------|
| `AudioController` | start/pause/resume/stop/status | Bearer host-operator JWT + `verifyHostOperatorToken(meetingId)` |
| `RecordingController` | recording-url, host-url | Dashboard JWT + `requireCreateMeeting` + creatorId 匹配；或废弃对外暴露，仅 dashboard 内签发 |
| `MeetingController` | POST create, start, end, PATCH schedule, list | Dashboard JWT + grant；`creatorId` 强制取自 token |
| `MeetingController` | GET minute | 始终要求 recording/dashboard JWT |
| `InternalMeetingController` | 全部 mutating | 与 `InternalMeetingAdminController` 一致：`internalApiAuth.requireToken` |

**可选（推荐）**：新增 `MeetingApiAuthFilter` 或 Spring Security 白名单，避免逐控制器遗漏。

**文件**：上述 Controller；参考 `MeetingHostController.verifyOperatorWrite`、`DashboardGrantService`。

---

### B1-03 · 路径穿越修复

| 字段 | 状态 |
|------|------|
| 审计项 | C6, M9 |
| 状态 | ⬜ 待做 |

**改动**

1. `AgendaMaterialDiskStorage.loadMeta` / `resolveDataPath`：
   - `fileId` 校验 `^[A-Za-z0-9-]+$`
   - `base.resolve(...).normalize()` + `startsWith(storageDir.normalize())`
2. `MeetingController.proxyFileImage`：调用前 `assertMeetingHasLocalFile(meetingId, fileId)`（与 download/preview 一致）。
3. `AudioCacheService.cachePathFor`：`meetingId` 校验 UUID 形态，拒绝 `/`、`\`、`..`。

**模板**：`TodoAttachmentService.resolveStoragePath:118-125`。

**文件**：`AgendaMaterialDiskStorage.java`、`MeetingController.java`、`AudioCacheService.java`。

---

### B1-04 · 飞书 Webhook fail-closed + 验签

| 字段 | 状态 |
|------|------|
| 审计项 | H1, M1 |
| 状态 | ⬜ 待做 |

**改动**

1. `verifyCardCallbackHeaderToken`：`verification-token` 为空 → **拒绝**（不再 `return true`）。
2. `handleSchema20FlatCardCallback`、`handleCardActionTriggerV1`、`handleWebhook` 全部走统一验签入口。
3. 读取 `X-Lark-Signature`，按飞书文档做 SHA256 HMAC 校验（需 `encrypt-key`）。
4. 内存/Redis LRU：`event_id` 去重，TTL 24h。

**文件**：`FeishuWebhookController.java`、可选 `FeishuWebhookDedupStore.java`。

---

### B1-05 · 内部 API 加固

| 字段 | 状态 |
|------|------|
| 审计项 | H4 |
| 状态 | ⬜ 待做 |

**改动**

1. `InternalApiAuth`：`MessageDigest.isEqual` 替代 `String.equals`。
2. `reload-enabled` 默认 `false`；`reload-token` 无默认值，未配置则拒绝。
3. 可选：`allowed-ips` 配置，非白名单 IP 直接 403。

**文件**：`InternalApiAuth.java`、`InternalReloadProperties.java`、`application.yml`。

---

## B2 · UX P0（用户主路径）

> 解决「快速开始 → 主持页 → 无法结束 / 会议尚未开始」及高频交互问题。

### B2-01 · Dashboard「开始/恢复」与 autostart

| 字段 | 状态 |
|------|------|
| UX 项 | H1, M11；业务：待开始只跳转不启动 |
| 状态 | 🟡 部分落地（autostart 机制已完成；「恢复」弹窗拦截 H1 未修） |

**改动**

1. `dashboard.html`：
   - ✅ `appendAutostartParam(url)`：待开始会议 URL 加 `autostart=1`。
   - ✅ detected「开始」、创建成功「直达链接」均经此函数。
   - ⬜ 「恢复」：`window.open` 改为同步开空白窗 + await 后赋值 `location.href`；若 `null` 则展示可点击链接。（当前 `dashboard.html:433-434` 仍是 await 后 `window.open`）
2. ✅ `MeetingWebPageUrls.recordingPageUrlWithAutostart`：飞书通知卡片链接带 autostart（新建草稿）。
3. `host-meeting.html`：
   - ✅ 读 `autostart=1` → 预填完成后自动 `startMeetingFlow()`；
   - ✅ 成功后 `clearAutostartQueryParam`；失败保留参数以便刷新重试。
   - ⬜ 未开始时禁用「结束会议」；提示取消预约回工作台。

**文件**：`dashboard.html`、`host-meeting.html`、`MeetingWebPageUrls.java`、`FeishuMeetingStartCoordinator.java`。

---

### B2-02 · 麦克风失败清理 + recorder 抛错

| 字段 | 状态 |
|------|------|
| UX 项 | H2, M9 |
| 状态 | ⬜ 待做 |

**改动**

1. `recorder.js`：`getUserMedia` 失败 **reject**，保留 `NotAllowedError` 等具体类型。
2. `host-meeting.html` `startMeetingFlow`：
   - 顺序：先 `connectRecorder()`，成功后再 `beginHostSession()`（避免服务端 STARTED 但无麦）；
   - 或 catch 中若已 POST /start 但录音失败 → `POST recording-session/end` 或专用 cancel 清理。
3. autostart 前显示一行说明：「即将请求麦克风权限，请点击允许」。

**文件**：`recorder.js`、`host-meeting.html`。

---

### B2-03 · Token 过期提示

| 字段 | 状态 |
|------|------|
| UX 项 | H3 |
| 状态 | ⬜ 待做 |

**改动**

1. `hostApi` / `refreshHostStateFromApi`：检测 `401` 或 `j.code` 鉴权失败 → `stopHostStatePoll()` + 全页 banner「登录已过期，请在飞书重新发送「会议管理」」。
2. `dashboard.html` `api()`：同样处理 401。

**文件**：`host-meeting.html`、`dashboard.html`。

---

### B2-04 · Dashboard 刷新与防重复提交

| 字段 | 状态 |
|------|------|
| UX 项 | H4, H5, M3 |
| 状态 | ⬜ 待做 |

**改动**

1. `pageshow`：`if (e.persisted) init()`；`popstate` 时 `init()`。
2. `initSeq`：每次 `init()` 自增，await 后序号变化则放弃写 `#app`。
3. `doScheduleMeeting`、todo 按钮：handler 开头 `disabled=true`，`finally` 恢复。
4. `api()`：非 JSON 错误体 → `请求失败 (status)`；网络错误 → 中文友好文案。

**文件**：`dashboard.html`。

---

### B2-05 · iOS / HTTPS 麦克风提示

| 字段 | 状态 |
|------|------|
| UX 项 | M4, M5 |
| 状态 | ⬜ 待做 |

**改动**

1. `recorder.js`：创建 `AudioContext` 后 `await audioContext.resume()`。
2. 非 HTTPS 且非 localhost：调用 getUserMedia 前 `showErr('麦克风需在 HTTPS 下使用…')`。

**文件**：`recorder.js`、`host-meeting.html`（扩展 `iosNoticeHost` 文案）。

---

## B3 · 业务逻辑 P1

### B3-01 · 结束路径统一

| 字段 | 状态 |
|------|------|
| 审计项 | H8；UX/业务：看门狗、飞书结束不一致 |
| 状态 | 🟡 部分落地（第1、2项已完成；第3、4项未做） |

**改动**

1. ✅ `DashboardService.endActiveMeeting` → `MeetingRecordingSessionEndService.endFromRecordingPage`（`DashboardService.java:230` 已调用）。
2. ✅ `FeishuCommandHandler.handleStopMeeting`：同上（`FeishuCommandHandler.java:628`）；查询/校验含 `PAUSED`（`:596-599`）。
3. ⬜ `RecordingTimeoutChecker`：结束走 `endFromRecordingPage`，非裸 `endMeeting`。（当前 `RecordingTimeoutChecker.java:72` 仍调 `meetingService.endMeeting`）
4. ⬜ `MeetingService.endMeeting` 内部调用 `meetingHostMediaTeardownService.beforeRecordingSessionEnd`（或禁止外部直接调用，仅经 SessionEndService）。

**文件**：`DashboardService.java`、`FeishuCommandHandler.java`、`RecordingTimeoutChecker.java`、`MeetingService.java`。

---

### B3-02 · 状态机条件更新 + Outbox 幂等键

| 字段 | 状态 |
|------|------|
| 审计项 | H5, H6, H7, H10 |
| 状态 | ⬜ 待做 |

**改动**

1. `endMeeting` / `stopRecording`：  
   `UPDATE int_meeting SET status='PROCESSING', ... WHERE id=? AND status IN ('RECORDING','PAUSED','STARTED')`  
   影响行数 0 → 跳过 `dispatchAfterMeetingEnded`。
2. `MeetingDomainEventOutboxListener`：`event_key = "offline-asr:" + meetingId`（去掉 `sentAt`）。
3. `resolveAndPersistAudioPath` / `attachCachedAudioRecording`：终态 `PROCESSING/COMPLETED/...` 只读，不写 `recordingStates`。
4. `AudioCacheService.writeAudioChunk`：DB 终态则 return；`AudioWebSocketHandler` 终态关 WS，不 `tryStartRecording`。

**文件**：`MeetingService.java`、`RecordingService.java`、`PostMeetingOrchestrator.java`、`MeetingDomainEventOutboxListener.java`、`AudioCacheService.java`、`AudioWebSocketHandler.java`。

---

### B3-03 · Host runtime 并发与泄漏

| 字段 | 状态 |
|------|------|
| 审计项 | H9 |
| 状态 | 🟡 部分落地（第3项已完成；第1、2项未做） |

**改动**

1. ⬜ `MeetingHostSessionService.start`：`synchronized (lockFor(meetingId))` 包裹；`putIfAbsent` 失败则 cancel 新 tick。（当前 `start` `:258-266` 未加 synchronized）
2. ⬜ 定时任务：扫描 `runtimes` 中 meetingId，DB 已终态 → `stopAndClear`。（当前无此 `@Scheduled` 方法）
3. ✅ `HOST_START_FORBIDDEN`：`PROCESSING/COMPLETED/CANCELLED/TODO_TRACKING` 禁止再次 start。（`MeetingHostSessionService.java:83-87`、`:271`）

**文件**：`MeetingHostSessionService.java`。

---

### B3-04 · Chroma 网格与预约 Tab

| 字段 | 状态 |
|------|------|
| UX/业务 | 模板卡片点击仍 create；预约无「开始」 |
| 状态 | ⬜ 待做 |

**改动**

1. `dashboard.html` `onItemClick`：若 item 带 `meetingState: draft|active`，调 API 取 `recordingUrl` 后 `window.open(appendAutostartParam(url))`；否则 `doCreateMeeting`。
2. 预约列表增加「开始」按钮（draft 态 + autostart）。

**文件**：`dashboard.html`。

---

## B4 · 其余（P1–P2，按需）

| ID | 项 | 文件 | 状态 |
|----|-----|------|------|
| B4-01 | Todo assign/split 鉴权 | `TodoController.java` | ⬜ |
| B4-02 | Voiceprint 页 XSS 转义 | `VoiceprintRegisterController.java` | ⬜ |
| B4-03 | CORS 收窄 origin | `WebConfig.java`、`WebSocketConfig.java` | ⬜ |
| B4-04 | Host WS 指数退避 + 断线 banner | `host-meeting.html` | ⬜ |
| B4-05 | aria-live 错误区 | `dashboard.html`、`host-meeting.html` | ⬜ |
| B4-06 | 预约过去时间客户端校验 | `dashboard.html` | ⬜ |
| B4-07 | `MeetingService.listMeetings` size 上限 | `MeetingController.java` | ⬜ |
| B4-08 | AudioNormalize 临时文件 + 原子 rename | `AudioNormalizeService.java` | ⬜ |
| B4-09 | 静态资源 `/rec/static/` 404 | 资源路径或 `WebConfig` 映射 | ⬜ |
| B4-10 | `forceStatus` 收窄用途 | `MeetingStateMachineService.java` | ⬜ |

---

## 测试清单（每批合并前）

### B1 安全

- [ ] 无 token 调用 `POST /api/v1/audio/{id}/stop` → 401/403
- [ ] 无 token 调用 `GET /api/v1/meetings/{id}/host-url` → 401/403
- [ ] 无 token 调用 `PUT /api/v1/internal/meetings/{id}` → 401/403
- [ ] `proxy-file-image?fileId=../` → 400
- [ ] Webhook 无 signature / 错误 token → 403
- [ ] 缺 `JWT_SECRET` 启动 → 失败

### B2 UX

- [ ] Dashboard 待开始「开始」→ 主持页自动开始（允许麦克风）
- [ ] 拒绝麦克风 → 服务端非 STARTED 或用户可再次开始
- [ ] 浏览器后退 dashboard → 列表刷新
- [ ] 双击「预约会议」→ 仅一场
- [ ] iOS Safari：AudioContext resume 后录音有数据

### B3 业务

- [ ] 并发双点「结束」→ 离线 ASR outbox 仅一条（同 meetingId）
- [ ] 结束后再开 WS 推 PCM → 不 append、不重启录音
- [ ] 看门狗超时结束 → host tick 停止
- [ ] PROCESSING 会议主持页「开始」→ 400 明确提示

---

## 发版顺序建议

```text
B0（运维轮换）
  → B1 + B2（可分两 PR，同一发版窗口）
  → B3（下一版）
  → B4（持续加固）
```

**回滚**：B1 鉴权过严可能导致旧脚本/未带 token 的集成失败，发版前确认 Admin、Feishu 卡片、monitor 等调用方已带 token。

---

## 状态维护

执行完成后请更新：

1. 本文档 Part C 对应条目的「状态」：⬜ → ✅（或 🟡 部分落地）
2. 本文档文首「当前状态」摘要
3. [版本迭代历史.md](./版本迭代历史.md) §2 发版记录（若发版）

---

*本方案为执行清单，不含密钥明文；凭据轮换仅通过 B0 运维步骤完成。*
