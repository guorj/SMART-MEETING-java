# 会中核心类 — JavaDoc 参考（待写入源码）

> 用途：为会中相关 Java 类补充**类注释、方法注释、@param / @return / @throws**。  
> 将下文各节复制到对应源文件，或切换到 **Agent 模式** 后让助手批量应用。

---

## 1. ParticipantCheckInService.java

**类注释**（置于 `public class` 之前）：

```java
/**
 * 线上参会人自动检点（到场登记）服务。
 *
 * <p>参会人通过个人入会链接 {@code /join/{meetingId}?token=…} 打开页面后，
 * 前端调用 {@code POST /api/v1/meetings/{id}/check-in}，本服务校验 JWT、更新参会人表，
 * 并同步主持端混合检点名单（{@link MeetingHostSessionService#recordOnlineCheckIn}）。
 *
 * <p>仅 {@link AttendanceMode#ONLINE} 参会人可走本链路；线下人员须在检点点名阶段现场答到。
 */
```

**方法 `checkInFromToken`**：

```java
    /**
     * 根据个人入会 JWT 完成线上到场登记。
     *
     * <p>若参会人记录不存在则按令牌信息插入（默认 {@code attendanceMode=ONLINE}）；
     * 已存在则更新 {@code checked_in_at}、{@code check_in_source=AUTO_ONLINE}、{@code status=CONFIRMED}。
     *
     * @param meetingId   会议主键，须与 JWT 内 {@code meetingId} 一致
     * @param bearerToken 个人入会令牌（不含 {@code Bearer } 前缀），由 {@link JwtUtil#verifyJoinToken} 校验
     * @return 登记结果，键包括 {@code meetingId}、{@code userId}、{@code name}、
     *         {@code checkedInAt}（ISO-8601 字符串）、{@code checkInSource}
     * @throws BusinessException 令牌无效、缺少 userId、或参会人登记为线下到场（400）
     */
```

---

## 2. MeetingCheckInController.java

**类注释**：

```java
/**
 * 线上参会人到场登记（检点）REST 接口。
 *
 * <p>路径前缀：{@code /api/v1/meetings}。与个人入会页 {@code join-meeting.html} 配合使用。
 *
 * @see ParticipantCheckInService
 */
```

**方法 `checkIn`**：

```java
    /**
     * 线上参会人登记到场。
     *
     * @param meetingId     会议主键
     * @param authorization HTTP 请求头 {@code Authorization}，格式为 {@code Bearer <token>}
     * @return 登记结果（meetingId、userId、name、checkedInAt、checkInSource）
     * @throws BusinessException 缺少或非法 Bearer（401）、业务校验失败（400）
     */
```

**方法 `bearer`**：

```java
    /**
     * 从 Authorization 请求头解析 Bearer 令牌。
     *
     * @param authorization 原始请求头值，须以 {@code Bearer } 开头（大小写不敏感）
     * @return 去掉前缀后的 JWT 字符串
     * @throws BusinessException 请求头为空或格式不正确时抛出 401
     */
```

---

## 3. ParticipantLinkService.java

**类注释**（替换现有简短注释）：

```java
/**
 * 参会人个人入会链接生成服务（混合参会 · 线上盘点用）。
 *
 * <p>为 {@link AttendanceMode#ONLINE} 的参会人签发个人 JWT 并拼接
 * {@code /join/{meetingId}?token=…} URL，供飞书单聊推送或会务补发。
 */
```

**方法 `buildParticipantLinks`**：

```java
    /**
     * 列出会议全部参会人，并为线上成员填充个人 {@code joinUrl}。
     *
     * @param meetingId 会议主键
     * @return 参会人 DTO 列表；ONLINE 且 userId 非空时含 joinUrl
     */
```

---

## 4. MeetingParticipantController.java

**类注释**：

```java
/**
 * 参会人及个人入会链接查询接口。
 *
 * <p>供会务补发线上同事入会链接；{@code joinUrl} 与个人绑定，勿在群内公开张贴。
 */
```

**方法 `participantLinks`**：

```java
    /**
     * 列出参会人及个人入会链接（ONLINE 参会人含 joinUrl）。
     *
     * @param meetingId 会议主键
     * @return {@code { meetingId, participants: [...] }}
     */
```

---

## 5. MeetingHostController.java

**类注释**（置于类上）：

```java
/**
 * AI 会议主持 REST API（主持页 {@code host-meeting.html} 调用）。
 *
 * <p>路径前缀：{@code /api/v1/host/meetings}。除 {@link #state} 使用录音页 JWT 外，
 * 其余接口均须主持操作员 JWT（{@link JwtUtil#verifyHostOperatorToken}）。
 *
 * <p>每次变更操作返回最新 {@code host_state} JSON，与主持 WebSocket 推送结构一致。
 *
 * @see MeetingHostSessionService
 */
```

**各端点方法注释模板**：

| 方法 | 说明 |
|------|------|
| `start` | 开启主持会话：加载议程、群静音、TTS 开场，条件满足时自动进入检点。`@param meetingId` `@param authorization` `@param body` 可选议程覆盖 |
| `pause` | 暂停议题/会议计时及检点 deadline，与录音暂停对齐。 |
| `resume` | 恢复计时，回补暂停时长。 |
| `nextTopic` | 当前议题标 COMPLETED，进入下一项 RUNNING 并 TTS 播报。 |
| `skipTopic` | 当前议题标 SKIPPED 后进入下一项。 |
| `extendTopic` | 为当前议题加时 1/3/5/10 分钟（body.minutes）。 |
| `state` | 查询主持状态（录音页 token 可读）。 |
| `rollCallStart` | 开始混合检点：先线上盘点再线下点名。 |
| `rollCallSkipCurrent` | 跳过当前待答到人员（仅线下点名阶段）。 |

**`bearer` 私有方法**：同 MeetingCheckInController。

---

## 6. RollCallAffirmationMatcher.java

**方法 `looksLikeQuestionOrInquiry`**：

```java
    /**
     * 判断转写文本是否像追问/核实，而非本人答到。
     *
     * @param t 已 trim 的定稿转写片段，可为 null
     * @return 含「吗」「到没」「为什么」等则为 true
     */
```

**方法 `matches`**：

```java
    /**
     * 判断定稿语音是否视为线下检点「答到」类肯定。
     *
     * <p>单麦、无声纹场景下的流程信任策略，不校验说话人身份。
     * 排除 TTS 回声「请某某答到」及他人代答类长句。
     *
     * @param raw ASR 定稿全文，可为 null
     * @return 匹配「答到」「在」「到了」等短肯定时为 true
     */
```

---

## 7. AsrBridgeService.java

在现有类注释下，为各 public 方法补充：

```java
    /**
     * 为指定会议建立讯飞实时 ASR 连接。
     *
     * @param meetingId 会议主键
     * @return 连接成功为 true；主 ASR 非 xfyun 或连接失败为 false
     */
    public boolean startRealtimeAsr(String meetingId)

    /**
     * 将浏览器 PCM 帧转发至讯飞（并写入本地音频缓存）。
     *
     * @param meetingId 会议主键
     * @param pcmData   16kHz/mono/s16le PCM，推荐每帧 1280 字节（40ms）；null 或空则忽略
     */
    public void sendAudioFrame(String meetingId, byte[] pcmData)

    /**
     * 结束指定会议的实时 ASR 会话（发送结束帧）。
     *
     * @param meetingId 会议主键
     */
    public void endRealtimeAsr(String meetingId)

    /**
     * 断开当前讯飞 WebSocket（全局，不区分会议）。
     */
    public void disconnect()

    /**
     * 强制结束会议 ASR 并断开讯飞连接（异常恢复用）。
     *
     * @param meetingId 会议主键
     */
    public void forceDisconnectAsr(String meetingId)

    /**
     * @return 配置的主 ASR 提供方标识，如 {@code xfyun}
     */
    public String getAsrProvider()

    /**
     * @return 讯飞客户端是否已连接
     */
    public boolean isAsrActive()
```

**私有方法 `onAsrResult`**：

```java
    /**
     * 讯飞转写回调：入库、推送前端、检点答到钩子。
     *
     * @param result 单条 ASR 结果，含 meetingId、text、final、isLast 等
     */
```

---

## 8. MeetingHostSessionService.java — 需补全/修正项

### 8.1 修正 `getAgendaDocRefs`（约 158–161 行，当前注释格式错误）

替换为：

```java
    /**
     * 主持进行中：返回指定会序已绑定的全部飞书资料引用。
     *
     * <p>优先读内存 {@link HostRuntime} 议程项；无主持会话或该项无资料时，
     * 回退 {@link PresetAgendaDocService#listDocRefsForAgenda}。
     *
     * @param meetingId   会议主键
     * @param agendaIndex   会序下标，0-based（会序 1 为 0）
     * @return 飞书 Docx/Wiki/Base 引用列表，可能为空列表
     */
    public List<FeishuDocRefDto> getAgendaDocRefs(String meetingId, int agendaIndex)
```

### 8.2 `getAgendaDocRef`（deprecated）

```java
    /**
     * @param meetingId   会议主键
     * @param agendaIndex   会序下标，0-based
     * @return 第一条飞书资料引用；无资料时为 null
     * @deprecated 请使用 {@link #getAgendaDocRefs}
     */
```

### 8.3 `AgendaDocRef` record

```java
    /**
     * 会序绑定的单条飞书资料引用（docx / wiki / base）。
     *
     * @param feishuDocUrl  飞书文档 HTTPS 链接
     * @param feishuDocKind 资料类型：DOCX、WIKI、BASE 等
     */
    public record AgendaDocRef(String feishuDocUrl, String feishuDocKind) {
```

### 8.4 构造器补全 @param

```java
     * @param participantMapper      参会人表（检点名单、线上模式）
     * @param presetAgendaDocService 会序飞书资料配置合并
```

### 8.5 `startRollCall`

```java
    /**
     * 开始混合检点：线上先盘点（{@code ONLINE_INVENTORY}），再线下逐一点名（{@code OFFLINE_ROLL_CALL}）。
     *
     * @param meetingId 会议主键
     * @throws BusinessException 未开启主持、检点进行中、应到名单为空、会议不存在
     */
```

### 8.6 `recordOnlineCheckIn`

```java
    /**
     * 线上参会人 check-in 后，同步更新主持端检点名单状态。
     *
     * <p>按 {@code userId} 或 {@code displayName} 匹配 {@link RollCallPerson}；
     * 无主持会话或名单为空时静默返回。
     *
     * @param meetingId   会议主键
     * @param userId      飞书 open_id，可为 null（则仅按姓名匹配）
     * @param displayName 显示姓名，可为 null
     */
```

### 8.7 内部类 `HostRuntime` / `RollCallPerson` / `HostTopic`

```java
    /** 单会议主持运行时：议程、计时、检点、tick 调度 */
    private static final class HostRuntime { ... }

    /**
     * 检点名单中的应到人员。
     * <p>status: PENDING / ANSWERED / MISSED / SKIPPED
     */
    private static final class RollCallPerson { ... }

    /** 主持议程一项（来自 host_agenda JSON 或 start 请求体） */
    private static final class HostTopic { ... }
```

---

## 9. MatterProgressReportService.java

**方法 `analyzeForMeeting`**：

```java
    /**
     * 为指定会议生成「事项进度通报」Markdown（会中/录音页会序 2 使用）。
     *
     * <p>数据源优先级：综合管理会 OpenClaw 多维表分支 → 飞书 Docx 正文 → classpath 样例（仅联调）。
     * 正文经 LLM 提炼为通报稿。
     *
     * @param meetingId 会议主键
     * @return 含 {@code analysisMarkdown}、{@code source}、{@code configName}
     * @throws BusinessException 会议不存在（404）、未配置文档或拉取失败（400/502）
     */
```

---

## 10. CheckInSource.java / AttendanceMode.java

见上文第 1 节配套枚举完整注释（`AUTO_ONLINE`、`ROLL_CALL`、`MANUAL`、`TIMEOUT` 及 `fromString`）。

---

## 应用方式

1. **手动**：按文件打开 `meeting-server/src/main/java/...`，将对应节粘贴到类/方法上方。  
2. **Agent 模式**：对助手说「按 `docs/会中核心类-JavaDoc参考.md` 写入所有 Java 源文件」。  
3. **生成 Javadoc 站点**（可选）：

```bash
cd smart-meeting-java/meeting-server
mvn -q javadoc:javadoc -Dshow=package
```

---

*文档版本：v1.0 · 2026-05-19*
