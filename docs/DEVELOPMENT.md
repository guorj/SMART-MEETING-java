# 开发文档 - 智能会议纪要系统 Java 版（会中阶段 P0）

> 版本：v0.3（**与仓库当前实现对齐**，以代码为准）  
> 日期：2026-05-09  
> 作者：阿统 🔧  
> 范围：会中核心链路（录音 → ASR → 校正 → 声纹 → 纪要 → 飞书文档）  
> 依据：PRD-Java-一期.md v2.1 + Python 版成功经验  

**说明**：v0.3 起本文档描述的是 `smart-meeting-java` 目录下**已实现**的包结构与接口；与早期 v0.1/v0.2 草案不一致之处，以本节及后续章节为准。若与 PRD 条目冲突，产品验收以 PRD 排期为准。

---

## 1. 项目结构

```
smart-meeting-java/
├── pom.xml                                    # Maven 父 POM（聚合 meeting-server）
├── docker-compose.yml                         # MySQL + Redis + Kafka + smart-meeting 应用（无独立 Nginx 服务）
├── nginx/
│   └── nginx.conf                             # 供宿主机或其它编排引用的反向代理示例（SSL + WS + API）
│
├── meeting-server/                            # 主服务 (Spring Boot 3.x)
│   ├── Dockerfile
│   ├── pom.xml
│   ├── src/main/resources/
│   │   ├── application.yml                    # 主配置
│   │   ├── application-dev.yml / application-prod.yml
│   │   ├── schema.sql                         # DDL 副本（与 sql/schema.sql 对齐）
│   │   ├── logback-spring.xml
│   │   └── static/                            # 运行时静态资源（录音页、recorder.js、worklet）
│   │       ├── index.html
│   │       ├── recorder.js
│   │       └── worklet/pcm-processor.js
│   └── src/main/java/com/smartmeeting/
│       ├── SmartMeetingApplication.java
│       │
│       ├── api/
│       │   ├── controller/
│       │   │   ├── MeetingController.java     # 会议创建/开始/结束/列表/上次进度
│       │   │   ├── RecordingController.java   # 录音 URL + JWT（GET recording-url）
│       │   │   ├── AudioController.java       # 录音 REST：start/pause/resume/stop（非 WebSocket）
│       │   │   ├── RecorderPageController.java# GET /rec/{meetingId} → forward 静态录音页
│       │   │   ├── FeishuWebhookController.java
│       │   │   ├── VoiceprintRegisterController.java  # 声纹注册页与提交 API
│       │   │   └── HealthController.java      # GET /api/v1/health
│       │   ├── config/
│       │   │   ├── WebConfig.java             # CORS + 静态资源映射（/static/**、/recorder/**、/worklet/**）
│       │   │   ├── WebSocketConfig.java
│       │   │   ├── AudioWebSocketHandler.java # WebSocket 音频：/ws/audio/{meetingId}
│       │   │   ├── KafkaConfig.java
│       │   │   ├── RedisConfig.java
│       │   │   └── AsyncConfig.java
│       │   └── dto/ …（MeetingCreateRequest、MeetingResponse、ApiResponse、PreviousProgressResponse 等）
│       │
│       ├── service/
│       │   ├── MeetingService.java
│       │   ├── RecordingService.java          # 录音状态 + 停录后发 Kafka / LocalEventBus
│       │   ├── AsrBridgeService.java
│       │   ├── AudioCacheService.java
│       │   ├── MinuteGenerationService.java
│       │   ├── OfflineCorrectionService.java
│       │   ├── VoiceprintService.java
│       │   ├── VoiceprintRegisterService.java
│       │   ├── TodoExtractionService.java
│       │   ├── FeishuService.java
│       │   ├── FeishuCommandRouter.java / FeishuCommandHandler.java / FeishuCardBuilder.java
│       │   └── …
│       │
│       ├── asr/（XfyunRealtimeClient、XfyunOfflineClient、XfyunIsvClient、AsrResult）
│       ├── repository/（MeetingMapper、ParticipantMapper、TranscriptMapper、TodoMapper、VoiceprintMapper、UserMappingMapper）
│       ├── entity/ / enums/ / model/ / exception/ / util/ / scheduled/
│       └── mq/
│           ├── KafkaProducer.java
│           ├── LocalEventBus.java               # 无 Kafka 时的本进程事件降级
│           ├── MinuteGenerateConsumer.java
│           └── TodoExtractConsumer.java
│
├── web/                                       # 录音前端源码目录（与 resources/static 同步维护）
│   ├── index.html                             # 页面 UI + 内联交互脚本（无独立 app.js）
│   ├── recorder.js
│   ├── styles/main.css
│   └── worklet/pcm-processor.js
│
└── sql/
    └── schema.sql                             # Docker MySQL 初始化使用的 DDL 源文件
```

**状态流转**：未单独抽取 `MeetingStateMachine` 类；合法流转在 `MeetingService`、`RecordingService` 等中以状态字符串校验完成。

---

## 2. 数据库设计（DDL）

### 2.1 建库语句

**以仓库 `sql/schema.sql` 为唯一事实来源**（Docker 初始化挂载该文件）。库名由部署环境决定：`docker-compose.yml` 使用环境变量 `DB_NAME`（常见为 `smart_meeting`）。文档示例：

```sql
CREATE DATABASE IF NOT EXISTS smart_meeting
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE smart_meeting;
```

### 2.2 会议主表 `int_meeting`

```sql
CREATE TABLE int_meeting (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会议UUID',
    title           VARCHAR(200) NOT NULL COMMENT '会议主题',
    agenda          JSON         NULL     COMMENT '议题列表 JSON',
    company         VARCHAR(200) NOT NULL COMMENT '所属集团',
    department      VARCHAR(200) NULL     COMMENT '集团部门',
    group_name      VARCHAR(200) NOT NULL COMMENT '会议组',
    status          VARCHAR(30)  NOT NULL DEFAULT 'ISSUE_COLLECTING' COMMENT '会议状态',
    creator_id      VARCHAR(64)  NOT NULL COMMENT '发起人飞书user_id',
    chat_id         VARCHAR(100) NULL     COMMENT '飞书群聊ID(用于消息推送)',
    room_id         VARCHAR(64)  NULL     COMMENT '会议室ID/视频会议ID',
    previous_meeting_id VARCHAR(36) NULL  COMMENT '上次会议ID（闭环关联）',
    scheduled_time  DATETIME     NULL     COMMENT '预定时间',
    actual_start_time DATETIME   NULL     COMMENT '实际开始时间',
    actual_end_time DATETIME     NULL     COMMENT '实际结束时间',
    duration_seconds INT         NULL     COMMENT '录音总时长(秒)',
    audio_path      VARCHAR(500) NULL     COMMENT '音频本地路径 /data/audio/{date}/{id}.pcm',
    doc_url         VARCHAR(500) NULL     COMMENT '飞书纪要文档URL',
    doc_token       VARCHAR(100) NULL     COMMENT '飞书文档token',
    recording_url   VARCHAR(500) NULL     COMMENT '录音页面URL',
    recording_token VARCHAR(500) NULL     COMMENT '录音页面JWT token',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_meeting_status (status),
    INDEX idx_meeting_creator (creator_id),
    INDEX idx_meeting_previous (previous_meeting_id),
    INDEX idx_meeting_company_group (company, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议主表';
```

### 2.3 参会人表 `int_meeting_participant`

```sql
CREATE TABLE int_meeting_participant (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '会议ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '飞书user_id',
    name            VARCHAR(100) NOT NULL COMMENT '姓名',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '确认状态',
    feature_id      VARCHAR(100) NULL     COMMENT '讯飞ISV声纹特征ID',
    voiceprint_ready TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '声纹就绪',
    todo_count      INT          NOT NULL DEFAULT 0 COMMENT '待办总数',
    completed_count INT          NOT NULL DEFAULT 0 COMMENT '已完成数',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    UNIQUE KEY uk_meeting_user (meeting_id, user_id),
    INDEX idx_participant_meeting (meeting_id),
    INDEX idx_participant_userid (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参会人表';
```

### 2.4 转录分段表 `int_transcript_segment`

```sql
CREATE TABLE int_transcript_segment (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '分段UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '会议ID',
    speaker_id      VARCHAR(100) NULL     COMMENT '说话人标识(speaker_N/姓名)',
    speaker_name    VARCHAR(100) NULL     COMMENT '说话人姓名',
    start_time_ms   INT          NOT NULL COMMENT '开始时间偏移(ms)',
    end_time_ms     INT          NOT NULL COMMENT '结束时间偏移(ms)',
    text            TEXT         NOT NULL COMMENT '转写/校正后文字',
    is_final        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否最终结果',
    confidence      DOUBLE       NULL     COMMENT 'ASR置信度(0-1)',
    corrected       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已校正',

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_segment_meeting_time (meeting_id, start_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转录分段表';
```

### 2.5 待办表 `int_meeting_todo`

```sql
CREATE TABLE int_meeting_todo (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '待办UUID',
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '来源会议ID',
    content         TEXT         NOT NULL COMMENT '待办内容',
    assignee_id     VARCHAR(64)  NOT NULL COMMENT '责任人飞书user_id',
    assignee_name   VARCHAR(100) NULL     COMMENT '责任人姓名',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '待办状态',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
    deadline        DATETIME     NULL     COMMENT '截止时间',
    completed_at    DATETIME     NULL     COMMENT '完成时间',
    completion_note TEXT         NULL     COMMENT '完成说明',
    block_reason    TEXT         NULL     COMMENT '卡点/延期原因',
    last_remind_at  DATETIME     NULL     COMMENT '上次提醒时间',
    remind_count    INT          NOT NULL DEFAULT 0 COMMENT '累计提醒次数',
    next_meeting_id VARCHAR(36)  NULL     COMMENT '下次会议ID',
    reported_in_next TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已通报',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE,
    INDEX idx_todo_meeting (meeting_id),
    INDEX idx_todo_assignee (assignee_id),
    INDEX idx_todo_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办表';
```

### 2.6 声纹表 `int_voiceprint`

```sql
CREATE TABLE int_voiceprint (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录UUID',
    user_id         INT          NOT NULL COMMENT 'OA用户ID(关联system_users)',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id',
    feature_id      VARCHAR(100) NOT NULL COMMENT '讯飞ISV声纹特征ID',
    group_id        VARCHAR(100) NULL     COMMENT '讯飞ISV声纹组ID',
    registered_at   DATETIME     NOT NULL COMMENT '注册时间',
    expires_at      DATETIME     NOT NULL COMMENT '过期时间',

    INDEX idx_voiceprint_feishu_user (feishu_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹表';
```

### 2.7 用户映射表 `int_user_mapping`

```sql
CREATE TABLE int_user_mapping (
    user_id         INT          NOT NULL PRIMARY KEY COMMENT 'OA用户ID',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id',
    feishu_union_id VARCHAR(100) NULL     COMMENT '飞书union_id',
    feishu_open_id  VARCHAR(100) NULL     COMMENT '飞书open_id',

    INDEX idx_feishu_user_id (feishu_user_id),
    INDEX idx_feishu_open_id (feishu_open_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OA用户↔飞书ID映射表';
```

---

## 3. 配置项（application.yml）

```yaml
server:
  port: 8765

spring:
  application:
    name: smart-meeting-server

  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:smart_meeting}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 10000

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 5000ms

  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      retries: 3
      acks: all
    consumer:
      group-id: smart-meeting
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      concurrency: 3
      ack-mode: manual

  # 静态资源（Web录音页）
  web:
    resources:
      static-locations: file:./web/,classpath:/static/

# ============================================================
# 业务配置
# ============================================================
meeting:
  audio:
    sample-rate: 16000
    bit-depth: 16
    channels: 1
    frame-duration-ms: 40
    max-duration-hours: 4
    silence-auto-pause-min: 30
    cache-retention-hours: 168
    cache-dir: /data/audio

  asr:
    primary: xfyun
    fallback: tencent
    xfyun:
      app-id: ${XFYUN_APP_ID}
      api-key: ${XFYUN_API_KEY}
      api-secret: ${XFYUN_API_SECRET}
      ws-url: wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1
      isv-url: https://api.xf-yun.com/v1/private/s1aa729d0
      isv-group-id: ${XFYUN_ISV_GROUP_ID:smart_meeting_vp}
      reconnect:
        max-retries: 5
        backoff-ms: 1000
        backoff-multiplier: 2.0
        max-backoff-ms: 30000

  llm:
    provider: ${LLM_PROVIDER:deepseek}
    api-key: ${LLM_API_KEY}
    api-url: ${LLM_API_BASE}
    model: ${LLM_MODEL:deepseek-v4-pro}
    timeout-seconds: 120
    max-retries: 2

  feishu:
    app-id: ${FEISHU_APP_ID}
    app-secret: ${FEISHU_APP_SECRET}
    base-url: https://open.feishu.cn
    token-cache-seconds: 7200

  jwt:
    secret: ${JWT_SECRET:change-me-in-production}
    expire-hours: 4

# 日志
logging:
  level:
    root: INFO
    com.smartmeeting: DEBUG
  file:
    path: /data/logs/smart-meeting
    max-size: 100MB
    max-history: 30
```

---

## 4. Kafka 主题设计

| Topic | 分区数 | 消费者组 | 说明 |
|-------|--------|----------|------|
| `meeting.events` | 3 | `minute-generate` | 会议结束事件 → 触发纪要生成链 |
| `todo.extract` | 3 | `todo-extract` | 纪要生成完成后 → 触发待办提取 |
| `todo.reminders.dlq` | 1 | `dlq-handler` | 死信队列（一期不用，预留） |

### 4.1 纪要生成消息体

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MinuteGenerateMessage {
    private String meetingId;       // 会议ID
    private String audioPath;       // 音频本地路径
    private List<String> featureIds; // 参会人声纹特征ID列表
    private String modelName;       // 大模型名称
    private Long sentAt;            // 发送时间戳
}
```

### 4.2 待办提取消息体

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TodoExtractMessage {
    private String meetingId;       // 会议ID
    private String minuteText;      // 纪要全文（由 minute-generate 写入后传递）
    private List<ParticipantInfo> participants; // 参会人列表（用于责任人匹配）
    private Long sentAt;
    
    @Data
    public static class ParticipantInfo {
        private String userId;
        private String name;
    }
}
```

### 4.3 消息流

```
POST /api/v1/meetings/{id}/start  (F-MID-01: 会议开始)
    ↓
MeetingService.startMeeting(meetingId)
    ↓ 无上次会议：状态 → STARTED
    ↓ 有上次会议且记录存在：
    │   ├─ 查询 int_meeting_todo（以上次会议 meeting_id）
    │   ├─ 统计完成/进行中/延期
    │   ├─ 状态 → REVIEWING（上次进度通报）
    │   └─ FeishuService.sendCardMessage(...) → 飞书卡片（优先 chat_id，否则 creator_id）
    ↓
等待用户打开录音页并调用录音 REST

POST /api/v1/audio/{meetingId}/start
    ↓
RecordingService.startRecording → 状态 RECORDING，落盘 audio_path

（浏览器 WebSocket /ws/audio/{meetingId} 经 AudioWebSocketHandler → AsrBridgeService → 讯飞实时 ASR）

POST /api/v1/audio/{meetingId}/stop
    ↓
RecordingService.stopRecording(meetingId)
    ↓ 更新状态 → PROCESSING，关闭 WS，汇总 featureIds
    ↓
KafkaProducer.send("meeting.events", MinuteGenerateMessage)
    （若 Kafka 不可用：MeetingService / RecordingService 可降级 LocalEventBus）
    ↓
MinuteGenerateConsumer 消费:
    1. 读取音频文件
    2. 调用讯飞离线 ASR 校正
    3. 调用讯飞 ISV 声纹识别 → 更新 speaker_name
    4. 更新 int_transcript_segment (corrected=true)
    5. 调用 LLM 生成纪要文本
    6. 创建飞书文档 → 更新 int_meeting(doc_url, doc_token)
    7. 更新状态 → COMPLETED
    8. 发送 todo.extract Topic → TodoExtractMessage
    ↓
TodoExtractConsumer 消费:
    1. 从纪要文本提取待办（LLM）
    2. 按参会人 name → userId 匹配责任人
    3. 写入 int_meeting_todo
    4. 更新状态 → TODO_TRACKING
    5. 推送飞书卡片通知发起人

（并列说明）POST /api/v1/meetings/{id}/end 也会发送 meeting.events 消息，与
POST /api/v1/audio/{meetingId}/stop 二选一即可，避免同一会议重复触发纪要链。
```

---

## 5. 核心 API 设计

### 5.1 会议与录音（REST）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建会议 | POST | `/api/v1/meetings` | 请求体可含 `participants`、`previousMeetingId`、`chatId` 等；**未**在服务端自动推算 `previousMeetingId` |
| 会议列表 | GET | `/api/v1/meetings` | 分页：`status`、`creatorId`、`page`、`size` |
| 获取会议 | GET | `/api/v1/meetings/{id}` | 查询会议详情 |
| **开始会议** | POST | `/api/v1/meetings/{id}/start` | **F-MID-01**：无上次会→`STARTED`；有上次会→`REVIEWING` 并推飞书进度卡片 |
| **结束并触发纪要（可选）** | POST | `/api/v1/meetings/{id}/end` | 将会议置 `PROCESSING` 并发送 `meeting.events`；与「音频停录」路径二选一 |
| **查询上次进度** | GET | `/api/v1/meetings/{id}/previous-progress` | **F-MID-02**：传入**当前会议** `id`，服务内解析 `previousMeetingId` 后统计上次待办 |
| 获取录音链接 | GET | `/api/v1/meetings/{id}/recording-url` | 返回 `url, token, meetingId, meetingTitle, expiresIn`；`url` 由 **`meeting.base-url`** 拼接 `/rec/{id}?token=...`（未配置时回退 `http://localhost:8765`） |

**录音控制（独立前缀，不在 `/meetings/.../recording` 下）**：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 开始录音 | POST | `/api/v1/audio/{meetingId}/start` | `STARTED`/`REVIEWING` → `RECORDING`，创建 PCM 文件 |
| 暂停录音 | POST | `/api/v1/audio/{meetingId}/pause` | 暂停写入与 ASR 转发 |
| 继续录音 | POST | `/api/v1/audio/{meetingId}/resume` | 恢复录音 |
| 结束录音 | POST | `/api/v1/audio/{meetingId}/stop` | → `PROCESSING`，发送 `meeting.events`（含 `featureIds`） |
| 录音状态 | GET | `/api/v1/audio/{meetingId}/status` | 内存态 `RECORDING` / `PAUSED`，未开始返回 404 |

**健康检查**：`GET /api/v1/health` → `{ code, message, timestamp }`。

**声纹注册（扩展）**：`GET /voiceprint?token=...`（HTML）；`POST /api/v1/voiceprint/register` 提交注册。

### 5.2 WebSocket 音频推流

| 端点 | 协议 | 说明 |
|------|------|------|
| `/ws/audio/{meetingId}?token={jwt}` | WebSocket (binary) | 浏览器→后端：PCM 帧 1280字节/40ms |

**握手参数**：
- `meetingId`: 路径参数
- `token`: JWT 鉴权 token（从 recording-url 获取）

**帧格式**：纯二进制 PCM（16kHz 16bit 单声道）

**心跳**：`AudioWebSocketHandler` 定时下发 JSON 文本 `{"type":"ping"}`；前端宜回 `pong` 或按实现约定处理（详见代码）。

### 5.3 飞书 Webhook

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 事件回调 | POST | `/api/v1/feishu/webhook` | 接收飞书 im.message.receive_v1 事件 |
| 卡片回调 | POST | `/api/v1/feishu/callback` | 处理卡片按钮回调（参会确认/待办完成等） |

### 5.4 待办管理（REST）

待办默认由 `TodoExtractConsumer` / `TodoExtractionService` 在纪要后写入 `int_meeting_todo`；下列接口用于查询与人工维护（状态值与 **`TodoStatus`** 枚举名一致：`PENDING`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`、`OVERDUE`、`DELAYED`）。

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 查看待办 | GET | `/api/v1/meetings/{id}/todos` | 返回 `MeetingTodoResponse[]`；按状态、截止时间、`created_at` 排序 |
| 更新状态 | PUT | `/api/v1/todos/{tid}/status` | JSON：`status`（必填），`completionNote`、`blockReason`（可选）。置为 `COMPLETED` 时写 `completed_at`；若责任人为该会参会人则同步 `completed_count` |
| 手动分配 | PUT | `/api/v1/todos/{tid}/assign` | JSON：`assigneeId`（必填），`assigneeName`（可选） |
| 待办看板 | GET | `/api/v1/meetings/{id}/todo-board` | 返回 `meetingTitle`、`statusCounts`（各状态数量）、与列表同序的 `todos` |

参会人可在 **创建会议** 时通过 `MeetingCreateRequest.participants` 一并提交；**无**单独 `POST /meetings/{id}/participants` 接口。

---

## 6. 核心类设计

### 6.1 实体类

```java
// Meeting.java
@Data @TableName("int_meeting")
public class Meeting {
    @TableId private String id;
    private String title;
    private String agenda;          // JSON 字符串
    private String company;
    private String department;
    private String groupName;
    private String status;             // 与 MeetingStatus 枚举名持久化为字符串
    private String creatorId;
    private String chatId;           // 飞书群聊 ID（卡片推送）
    private String roomId;
    private String previousMeetingId;
    private LocalDateTime scheduledTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationSeconds;
    private String audioPath;
    private String docUrl;
    private String docToken;
    private String recordingUrl;
    private String recordingToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// Participant.java
@Data @TableName("int_meeting_participant")
public class Participant {
    @TableId private String id;
    private String meetingId;
    private String userId;          // 飞书 user_id
    private String name;
    private String status;           // PENDING / CONFIRMED …
    private String featureId;
    private Boolean voiceprintReady;
    private Integer todoCount;
    private Integer completedCount;
}

// TranscriptSegment.java
@Data @TableName("int_transcript_segment")
public class TranscriptSegment {
    @TableId private String id;
    private String meetingId;
    private String speakerId;
    private String speakerName;
    private Integer startTimeMs;
    private Integer endTimeMs;
    private String text;
    private Boolean isFinal;
    private Double confidence;
    private Boolean corrected;
}

// MeetingTodo.java
@Data @TableName("int_meeting_todo")
public class MeetingTodo {
    @TableId private String id;
    private String meetingId;
    private String content;
    private String assigneeId;
    private String assigneeName;
    private String status;
    private String priority;
    private LocalDateTime deadline;
    private LocalDateTime completedAt;
    private String completionNote;
    private String blockReason;
    private LocalDateTime lastRemindAt;
    private Integer remindCount;
    private String nextMeetingId;
    private Boolean reportedInNext;
    private LocalDateTime createdAt;
}

// Voiceprint.java
@Data @TableName("int_voiceprint")
public class Voiceprint {
    @TableId private String id;
    private Integer userId;         // OA 用户ID
    private String userName;
    private String feishuUserId;
    private String featureId;
    private String groupId;
    private LocalDateTime registeredAt;
    private LocalDateTime expiresAt;
}

// UserMapping.java
@Data @TableName("int_user_mapping")
public class UserMapping {
    @TableId private Integer userId;
    private String userName;
    private String feishuUserId;
    private String feishuUnionId;
    private String feishuOpenId;
}
```

### 6.2 枚举类

```java
public enum MeetingStatus {
    ISSUE_COLLECTING, ATTENDEE_CONFIRMING, AGENDA_SENT, INVITED,
    STARTED, REVIEWING, RECORDING, PAUSED,
    PROCESSING, COMPLETED, TODO_TRACKING, ALL_DONE, ARCHIVED,
    ABORTED, CANCELLED;
}

public enum ConfirmStatus { PENDING, CONFIRMED, DECLINED, TIMEOUT_CONFIRMED; }
public enum TodoStatus { PENDING, IN_PROGRESS, COMPLETED, DELAYED, CANCELLED; }
public enum Priority { HIGH, MEDIUM, LOW; }
```

### 6.3 统一响应

```java
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(0); r.setMessage("success"); r.setData(data);
        return r;
    }
    public static <T> ApiResponse<T> error(int code, String msg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code); r.setMessage(msg);
        return r;
    }
}
```

### 6.3.1 关键 DTO

```java
/** 上次会议待办进度响应 */
@Data @Builder
public class PreviousProgressResponse {
    private String lastMeetingId;           // 上次会议ID
    private String lastMeetingTitle;        // 上次会议标题
    private LocalDateTime lastMeetingTime;  // 上次会议结束时间
    private int totalCount;                 // 总待办数
    private int completedCount;             // 已完成数
    private int inProgressCount;            // 进行中数
    private int delayedCount;               // 已延期数
    private List<DelayedItem> delayedItems; // 延期项详情

    @Data
    public static class DelayedItem {
        private String content;           // 待办内容
        private String assigneeName;      // 责任人姓名
        private String blockReason;       // 延期原因
        private LocalDateTime deadline;   // 原截止时间
    }
}

/** 会议开始响应（DTO 已存在；当前 HTTP 接口未使用该类） */
@Data @Builder
public class StartMeetingResponse {
    private String meetingId;
    private MeetingStatus newStatus;
    private PreviousProgressResponse previousProgress;
}
```

实际 **`POST /api/v1/meetings/{id}/start`** 返回 **`ApiResponse<MeetingResponse>`**；上次进度卡片在 `MeetingService.startMeeting` 内直接拼装并调用 `FeishuService.sendCardMessage`。

### 6.4 REST 控制器（会议 / 录音 URL）

`MeetingController`（`/api/v1/meetings`）：`POST /` 创建，`GET /{id}` 详情，`GET /` 列表，`POST /{id}/start` 开始会议，`POST /{id}/end` 结束并触发纪要消息，`GET /{id}/previous-progress` 上次待办统计，`GET /{id}/todos` 待办列表，`GET /{id}/todo-board` 待办看板。

`TodoController`（`/api/v1/todos`）：`PUT /{tid}/status` 更新待办状态，`PUT /{tid}/assign` 手动改责任人。

`RecordingController`（同前缀 `/api/v1/meetings`）：`GET /{id}/recording-url` 签发 JWT 并返回录音页 URL。

`AudioController`（`/api/v1/audio`）：`POST /{meetingId}/start|pause|resume|stop`，`GET /{meetingId}/status`。

`RecorderPageController`：`GET /rec/{meetingId}` → `forward:/static/index.html`。

### 6.5 AudioWebSocketHandler（WebSocket）

Spring `WebSocketHandler` 实现，注册路径 **`/ws/audio/{meetingId}?token=`**。负责 JWT 校验、与 `AsrBridgeService` 协同转发 PCM、暂停态丢帧、定时 `ping` 文本帧、会话替换与关闭清理。详见源码 `api/config/AudioWebSocketHandler.java`。

### 6.6 AsrBridgeService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AsrBridgeService {

    private final XfyunRealtimeClient xfyunClient;
    private final AudioWebSocketHandler audioWebSocketHandler;
    private final AudioCacheService audioCacheService;

    /**
     * 流程: 浏览器 WS → AudioWebSocketHandler → AsrBridgeService（组帧 1280B）→ XfyunRealtimeClient
     * 回传: 讯飞回调 → 本类 → AudioWebSocketHandler → 浏览器
     */
    // 具体方法见源码：按 meetingId 缓冲 PCM、推送转写 JSON 等
}
```

### 6.7 XfyunRealtimeClient

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class XfyunRealtimeClient {

    private final MeetingProperties props;
    private WebSocketSession session;
    private final List<String> reconnectBackoff = List.of(1000L, 2000L, 4000L, 8000L, 30000L);

    /**
     * 讯飞实时 ASR WebSocket 客户端
     * 
     * 协议:
     * - 握手: wss://...?authorization=...&date=...&host=...
     * - 帧格式: {"common":{"app_id":"xxx"},"business":{"language":"autodialect","role_type":2,...},"data":{"status":0,"format":"audio/L16;rate=16000","encoding":"raw","audio":"<base64>"}}
     * - 返回: {"code":0,"message":"success","data":{"status":2,"result":{"cg_status":2,"rg":[{"sc":0.95,"ws":[{"bg":0,"cw":[{"sc":0.95,"w":"你好"}]}]}]}}}
     */
    public void connect(String appId, String apiKey, String apiSecret) {
        // 1. 生成 HMAC-SHA1 签名
        // 2. 建立 WebSocket 连接
        // 3. 发送开始帧（business 配置）
    }

    public void sendAudio(byte[] pcmData) {
        // 发送音频帧（base64 编码，status=1 持续中）
    }

    public void end() {
        // 发送结束帧（status=2），等待最终结果
    }

    public void setTranscriptCallback(Consumer<AsrResult> callback) {
        // 设置转写结果回调
    }
}

@Data
public class AsrResult {
    private String text;          // 识别文字
    private Double confidence;    // 置信度
    private Boolean isFinal;      // 是否最终结果
    private Integer startTimeMs;  // 时间偏移
    private Integer endTimeMs;
    private String speakerId;     // 说话人编号（ASR 盲分）
}
```

### 6.8 XfyunSignatureUtil

```java
public class XfyunSignatureUtil {

    /**
     * 生成讯飞 WebSocket 鉴权 URL 参数
     * 算法: HMAC-SHA256 签名 → Base64 → URL 编码
     */
    public static String generateAuthUrl(String apiUrl, String appId, String apiKey, String apiSecret) {
        // 1. 构造签名原串: "host: ...\ndate: ...\nGET /ast/communicate/v1 HTTP/1.1"
        // 2. HMAC-SHA256 签名
        // 3. Base64 编码
        // 4. 组装 URL: wss://...?authorization=...&date=...&host=...
    }

    /**
     * 生成讯飞 ISV API 鉴权头
     */
    public static Map<String, String> generateIsvHeaders(String url, String appId, String apiKey, String apiSecret) {
        // HMAC-SHA256 签名 + 时间戳 + API Key
    }
}
```

### 6.9 MinuteGenerateConsumer（Kafka 消费者）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MinuteGenerateConsumer {

    private final OfflineCorrectionService correctionService;
    private final VoiceprintService voiceprintService;
    private final MinuteGenerationService generationService;
    private final FeishuService feishuService;
    private final MeetingService meetingService;
    private final KafkaTemplate<String, TodoExtractMessage> kafkaTemplate;

    @KafkaListener(topics = "meeting.events", groupId = "minute-generate")
    public void consume(MinuteGenerateMessage msg, Acknowledgment ack) {
        try {
            // 1. 读取音频文件
            // 2. 调用离线 ASR 校正（低置信度片段→LLM纠错）
            // 3. 调用 ISV 声纹识别 → 映射 speaker_N → 真实姓名
            // 4. 更新 int_transcript_segment (speaker_name, corrected=true)
            // 5. 组装完整转写文本
            // 6. 调用 LLM 生成纪要（Prompt + JSON 容错解析）
            // 7. 创建飞书文档 → 更新 int_meeting(doc_url, doc_token)
            // 8. 状态 → COMPLETED
            // 9. 发送 Kafka todo.extract Topic
            // 10. 推送飞书卡片"纪要已生成"
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("纪要生成失败: meetingId={}", msg.getMeetingId(), e);
            // 重试3次后标记 ABORTED
        }
    }
}
```

### 6.10 TodoExtractConsumer（Kafka 消费者）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoExtractConsumer {

    private final TodoExtractionService extractionService;
    private final MeetingService meetingService;
    private final FeishuService feishuService;

    @KafkaListener(topics = "todo.extract", groupId = "todo-extract")
    public void consume(TodoExtractMessage msg, Acknowledgment ack) {
        try {
            // 1. 调用 LLM 从纪要文本提取待办
            // 2. 按参会人 name → userId 匹配责任人
            //    - 唯一匹配 → 写入 assigneeId
            //    - 多人同名 → 标注"待确认"，assigneeId 留空
            //    - 无法匹配 → assigneeId 留空
            // 3. 批量写入 int_meeting_todo
            // 4. 更新 int_meeting_participant.todoCount
            // 5. 状态 → TODO_TRACKING
            // 6. 推送飞书卡片"待办已同步，共X项"
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("待办提取失败: meetingId={}", msg.getMeetingId(), e);
        }
    }
}
```

### 6.11 FeishuService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FeishuService {

    private final FeishuProperties props;
    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    /**
     * 获取 tenant_access_token（带 Redis 缓存）
     */
    public String getTenantAccessToken() {
        // 1. 从 Redis 获取缓存
        // 2. 过期则重新调用 POST /auth/v3/tenant_access_token/internal
        // 3. 写入 Redis（TTL = 7200s - 300s 缓冲）
    }

    /**
     * 发送飞书卡片消息
     */
    public void sendCard(String receiveId, String receiveIdType, String cardJson) {
        // POST /im/v1/messages?receive_id_type=user_id
    }

    /**
     * 创建飞书文档
     */
    public FeishuDocResult createDoc(String title, String folderToken) {
        // POST /docx/v1/documents
    }

    /**
     * 写入飞书文档内容（Block 结构化写入）
     */
    public void writeDocBlocks(String docToken, List<DocBlock> blocks) {
        // POST /docx/v1/documents/{doc_id}/blocks/{block_id}/children
    }

    /**
     * 通过 user_id 获取用户信息（姓名等）
     */
    public FeishuUserInfo getUserInfo(String userId) {
        // GET /contact/v3/users/{user_id}?user_id_type=user_id
    }

    /**
     * 发送交互卡片（实现名以源码为准，例如 sendCardMessage：接收 Markdown 元素列表）
     * F-MID-02 进度通报在 MeetingService.startMeeting 内拼装 elements 后调用。
     */
    public boolean sendCardMessage(String chatId, String title, List<Map<String, String>> elements) {
        // POST /im/v1/messages …（首参为会话/群 ID，详见源码）
    }
}
```

### 6.12 MeetingService（核心编排方法）

```java
@Service
@Slf4j
public class MeetingService {
    // MeetingMapper, ParticipantMapper, TodoMapper, FeishuService,
    // LocalEventBus,（可选）KafkaProducer …

    /**
     * F-MID-02：入参为「当前会议 id」，内部读取其 previousMeetingId 再统计上次会议待办。
     * 待办状态字段在库中为字符串，与 TodoStatus 枚举名比较。
     */
    public PreviousProgressResponse getPreviousProgress(String meetingId) { ... }

    public MeetingResponse startMeeting(String meetingId) { ... }

    public MeetingResponse endMeeting(String meetingId) { ... }  // 触发纪要消息（Kafka / LocalEventBus）

    // 无 MeetingStateMachine；状态在各自方法中校验并写回 Meeting.status 字符串列
}
```

### 6.13 定时任务

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RecordingTimeoutChecker {

    private final MeetingMapper meetingMapper;
    private final MeetingService meetingService;
    private final FeishuService feishuService;

    // 每 10 分钟检查
    @Scheduled(cron = "0 */10 * * * ?")
    public void check() {
        // 1. 查询 status=RECORDING/PAUSED 的会议
        // 2. 录音超4h → 自动 stopRecording()
        // 3. 静音超30min → 自动 pause + 推送飞书提醒
    }
}

@Component
@RequiredArgsConstructor
@Slf4j
public class AudioCacheCleaner {

    @Value("${meeting.audio.cache-retention-hours:168}")
    private int retentionHours;

    @Value("${meeting.audio.cache-dir:/data/audio}")
    private String cacheDir;

    // 每天 3:00 执行
    @Scheduled(cron = "0 0 3 * * ?")
    public void clean() {
        // 扫描 /data/audio/ 下超过 retentionHours 的文件，删除
    }
}
```

---

## 7. Web 录音页

**源码目录**: 仓库根目录 `web/`（含 `styles/main.css`）。**运行时**：同名静态资源打包在 `meeting-server/src/main/resources/static/`，由 Spring 通过 `WebConfig` 映射 `classpath:/static/`（`/static/**`、`/worklet/**` 等）。

| 文件 | 说明 |
|------|------|
| `index.html` | 页面 UI；**交互脚本内联于 HTML**（无独立 `app.js`） |
| `recorder.js` | AudioContext → AudioWorklet → PCM → WebSocket |
| `worklet/pcm-processor.js` | 重采样至 16kHz |

**入口 URL（与 `RecordingController` 一致）**：`{meeting.base-url}/rec/{meetingId}?token=...` → `RecorderPageController` 转发至 `/static/index.html`。本地示例：`http://localhost:8765/rec/{meetingId}?token={jwt}`。

### 7.1 录音页参数传递

```
http://{host}:{port}/rec/{meetingId}?token={jwt}&name={userName}
```

- `meetingId`: 会议 ID，用于 WebSocket 路由
- `token`: JWT 鉴权 token（4h 有效）
- `name`: 用户姓名（可选，用于前端显示）

### 7.2 WebSocket 断线重连（前端）

```javascript
// recorder.js 中实现
const MAX_RECONNECT = 3;
const RECONNECT_DELAYS = [1000, 3000, 5000]; // 指数退避

function connectWebSocket(meetingId, token) {
    ws = new WebSocket(`wss://${host}/ws/audio/${meetingId}?token=${token}`);
    ws.binaryType = 'arraybuffer';
    
    ws.onclose = () => {
        if (reconnectAttempts < MAX_RECONNECT) {
            const delay = RECONNECT_DELAYS[reconnectAttempts];
            // 缓存断线期间音频帧
            setTimeout(() => {
                reconnectAttempts++;
                connectWebSocket(meetingId, token);
                // 重连后补传缓存的音频帧
            }, delay);
        }
    };
}
```

---

## 8. Nginx 配置

```nginx
server {
    listen 443 ssl http2;
    server_name meeting.example.com;

    ssl_certificate     /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    # 录音页（与后端 RecorderPageController 对齐）
    location /rec/ {
        proxy_pass http://127.0.0.1:8765;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 可选：纯静态托管时可保留 /recorder/**（WebConfig 中亦有 classpath 映射示例）
    location /recorder/ {
        root /usr/share/nginx/html/web;
        try_files $uri $uri/ /recorder/index.html;
    }

    # 静态资源（与 Spring /static/** 一致时可用）
    location /static/ {
        proxy_pass http://127.0.0.1:8765;
        expires 7d;
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://127.0.0.1:8765;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400s;  # 24h
        proxy_send_timeout 86400s;
        proxy_buffering off;
    }

    # API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8765;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 飞书 Webhook
    location /api/v1/feishu/webhook {
        proxy_pass http://127.0.0.1:8765;
        proxy_set_header Host $host;
    }
}
```

---

## 9. Docker Compose

```yaml
version: "3.8"

services:
  smart-meeting:
    build:
      context: ./meeting-server
      dockerfile: Dockerfile
    container_name: smart-meeting-bot
    restart: unless-stopped
    ports:
      - "8765:8765"
    env_file:
      - .env
    environment:
      - KAFKA_SERVERS=kafka:9092
      - REDIS_HOST=redis
      - DB_HOST=mysql
    volumes:
      - meeting-data:/data
      - meeting-web:/usr/share/nginx/html/web
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8765/api/v1/health"]
      interval: 30s
      timeout: 5s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: "2.0"
          memory: 4G
        reservations:
          cpus: "1.0"
          memory: 1G

  mysql:
    image: mysql:8.0
    container_name: smart-meeting-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME}
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/schema.sql:/docker-entrypoint-initdb.d/schema.sql
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: smart-meeting-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: apache/kafka:3.7.0
    container_name: smart-meeting-kafka
    restart: unless-stopped
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    volumes:
      - kafka-data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-topics.sh", "--list", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 10

volumes:
  meeting-data:
  meeting-web:
  mysql-data:
  redis-data:
  kafka-data:
```

**说明**：当前 `docker-compose.yml` **未定义**独立 `nginx` 服务；仅包含 `smart-meeting`、`mysql`、`redis`、`kafka`。`meeting-web` 卷为预留，可与自定义 Nginx 或 CI 打包流程结合。TLS 与域名反代请参考根目录 `nginx/nginx.conf` 自行挂载。

---

## 10. Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装 curl（健康检查用）
RUN apk add --no-cache curl

# 复制 JAR
COPY target/meeting-server-0.1.0.jar app.jar

# 复制 Web 前端
COPY ../web /usr/share/nginx/html/web

# JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxRAMPercentage=75.0"

EXPOSE 8765

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**说明**：`COPY ../web` 仅在 Docker **构建上下文**包含上级目录时有效；当前默认 `context: ./meeting-server` 时可能需改为复制 `src/main/resources/static` 或调整构建参数——**以仓库内 `meeting-server/Dockerfile` 与 CI 脚本为准**。

---

## 11. 开发计划

### 11.1 分期时间表

| 阶段 | 天数 | 内容 | 里程碑 |
|------|------|------|--------|
| **Day 1** | 1 | 项目骨架初始化 + DDL + 基础配置 + MySQL/Redis/Kafka Docker 化 | `/api/v1/health` 返回 200 |
| **Day 2** | 1 | 会议 API + **开始会议(F-MID-01)** + **上次进度(F-MID-02)** + JWT + 录音 URL；创建时可带 `participants` | 创建会议 + 开始会议(含卡片) + `GET recording-url` |
| **Day 3** | 1 | 静态资源 + `GET /rec/{id}` + WebSocket `/ws/audio/{id}`（PCM） | 录音页可打开，WS 可收帧 |
| **Day 4** | 1 | 讯飞实时 ASR 桥接 + 音频缓存写入 | 浏览器录音 → 后端 → 讯飞转写 → 回显前端 |
| **Day 5** | 1 | 录音暂停/继续/结束（`/api/v1/audio/...`）+ 飞书 Webhook 基础 | 完整录音流程可跑通 |
| **Day 6** | 1 | Kafka 异步链：停录或 `end` → MinuteGenerate → 离线校正 | 结束录音后 Kafka 消息可消费 |
| **Day 7** | 1 | LLM 纪要生成 + 飞书文档创建 + 状态更新 | 纪要生成完成 → 飞书文档可访问 |
| **Day 8** | 1 | TodoExtract 消费者 + 待办提取 + 责任人匹配 | 待办写入 DB + 飞书卡片推送 |
| **Day 9** | 1 | 声纹识别集成 + 说话人映射 + 录音超时检查 | 说话人标注为真实姓名 |
| **Day 10** | 1 | 全链路联调 + 集成测试 + Bug 修复 | `docker-compose up` 一键启动可完整使用 |

### 11.2 每日产出标准

| 天 | 可验证的产出 |
|----|------------|
| Day 1 | `curl http://localhost:8765/api/v1/health` → `{"code":0}` |
| Day 2 | `POST /api/v1/meetings` 创建会议，数据库有记录 |
| Day 3 | 浏览器打开 `http://localhost:8765/rec/{id}?token=xxx`（或经 Nginx 反代），WebSocket 连接成功 |
| Day 4 | 对着麦克风说话，浏览器实时显示转写文字 |
| Day 5 | 点击"结束录音"，音频文件写入磁盘，会议状态更新 |
| Day 6 | Kafka Topic 收到消息，消费者开始处理 |
| Day 7 | 飞书收到卡片消息，点击跳转到纪要文档 |
| Day 8 | 飞书收到待办卡片，数据库有待办记录 |
| Day 9 | 纪要文档中说话人显示为真实姓名（张三、李四） |
| Day 10 | 端到端全流程：创建会议→录音→结束→纪要→待办，全链路自动跑通 |

---

## 12. 风险与预案

| 风险 | 等级 | 影响 | 预案 |
|------|------|------|------|
| 讯飞 ASR WebSocket 断连 | 🔴 高 | 实时转写中断 | 指数退避重连（1s/2s/4s/8s/30s），最大5次；断线期间音频帧本地缓存，重连后补传 |
| WebSocket 音频丢失 | 🟡 中 | 转写文字缺失片段 | 前端 AudioWorklet 本地缓存断线帧，重连后补传；后端接收端做序列号校验 |
| 大模型 JSON 解析失败 | 🟡 中 | 纪要无法结构化 | 三层容错：①标准 JSON 解析 ②正则提取 ③LLM 重试修正 |
| Kafka 消费者消费失败 | 🟡 中 | 纪要/待办不生成 | 重试3次 → 记录错误日志 → 推送飞书告警给发起人 |
| 声纹识别失败 | 🟢 低 | 说话人显示为 speaker_N | 降级为盲分模式，用户可手动修正 |
| 飞书 token 过期 | 🟢 低 | 消息/文档操作失败 | Redis 缓存 + 自动刷新 + 失败重试获取新 token |
| JVM OOM | 🟢 低 | 服务崩溃 | G1GC + MaxRAMPercentage=75；WebSocket 空闲超时关闭；音频流做背压控制 |
| MySQL 连接池耗尽 | 🟡 中 | API 超时 | HikariCP 连接池监控 + 慢查询告警 + 连接数调优 |

---

## 13. Python → Java 迁移对照表

| Python 模块 | 代码量 | Java 对应 | 预计代码量 |
|------------|--------|----------|-----------|
| `main.py` (FastAPI 入口) | 1912行 | `SmartMeetingApplication.java` + Controllers | ~800行 |
| `config.py` | 209行 | `application.yml` + `@ConfigurationProperties` | ~100行 |
| `meeting/store.py` | 369行 | `MeetingMapper.java` + `MeetingService.java` | ~600行 |
| `meeting/int_mysql_store.py` | 418行 | MyBatis-Plus + Repository | ~400行 |
| `meeting/models.py` | 70行 | Entity 类 × 5 | ~400行 |
| `meeting/auth.py` | 97行 | `JwtUtil.java` | ~100行 |
| `audio/connection_manager.py` | 80行 | `AudioWebSocketHandler.java` + `AudioController.java`(REST) | ~200行 |
| `asr/xfyun_client.py` | 315行 | `XfyunRealtimeClient.java` | ~400行 |
| `asr/offline_client.py` | 283行 | `XfyunOfflineClient.java` | ~300行 |
| `asr/audio_converter.py` | 154行 | `AudioUtil.java` | ~100行 |
| `llm/summary.py` | 273行 | `MinuteGenerationService.java` | ~300行 |
| `feishu/` (全套) | 1516行 | `FeishuService.java` + DTOs | ~800行 |
| `background/tasks.py` | 127行 | `@Scheduled` 定时任务 | ~150行 |
| `web/` (前端) | ~800行 | 直接复用，不修改 | 0行 |
| **总计** | **~7300行** | | **~4650行** |

> **注**：Java 代码量少于 Python 是因为 MyBatis-Plus 自动生成 CRUD，实体类由 Lombok 简化，且 Kafka/Redis/Spring Security 等中间件用 Starter 自动配置。但实际加上配置文件、异常处理、单元测试后，总体工作量与 Python 版相当。

---

## 14. 测试策略

### 14.1 单元测试

| 模块 | 测试内容 | 工具 |
|------|---------|------|
| `XfyunSignatureUtil` | HMAC-SHA256 签名生成、URL 拼接 | JUnit 5 + 已知测试向量 |
| `JwtUtil` | token 生成/验证/过期 | JUnit 5 |
| `MeetingService` / `RecordingService` 状态分支 | 合法/非法状态流转 | JUnit 5 + 参数化测试（无独立 StateMachine 类） |
| `AudioUtil` | PCM 格式校验、帧大小计算 | JUnit 5 |
| `FeishuService` | token 缓存/刷新逻辑 | JUnit 5 + MockRestServiceServer |

### 14.2 集成测试

| 场景 | 验证点 |
|------|--------|
| 创建会议（含 participants）→ 生成录音 URL | 数据正确写入 DB，返回完整信息 |
| WebSocket 连接 → 发送 PCM → 接收转写 | 音频帧转发、ASR 响应、前端回显 |
| 结束录音 → Kafka 消费 → 文档生成 | 状态流转、消息消费、飞书文档创建 |
| 待办提取 → 责任人匹配 → 写入 DB | 名称匹配逻辑、待办数据正确 |

### 14.3 端到端测试

```
1. POST /api/v1/meetings → 创建会议（body 中带 `previousMeetingId`、`participants` 等）
2. POST /api/v1/meetings/{id}/start → 开始会议（无上次会→STARTED；有上次会→REVIEWING）
3. GET /api/v1/meetings/{id}/previous-progress → 验证上次进度（首次会议无上次则返回 `data:null`）
4. GET /api/v1/meetings/{id}/recording-url → 获取录音 URL + token
5. 浏览器打开 `/rec/{id}?token=...` → `POST /api/v1/audio/{id}/start` 开始录音
6. 说话约 30 秒 → 实时转写显示
7. `POST /api/v1/audio/{id}/stop` 结束录音 → 等待 3–5 分钟（Kafka 或 LocalEventBus）
8. 飞书收到「纪要已生成」类通知（若已配置）
9. 打开飞书文档 → 验证纪要内容
10. 飞书收到待办同步通知；`GET /api/v1/meetings/{id}/todos` 或 `GET .../todo-board` 验证列表与统计
```

---

## 15. 上线检查清单

### 15.1 环境

- [ ] MySQL 8.0 已部署，schema.sql 执行成功
- [ ] Redis 7 已部署，可正常连接
- [ ] Kafka 3.7 已部署，Topic 已创建
- [ ] Nginx 已配置 SSL 证书，WebSocket 代理正常
- [ ] .env 文件已配置所有必填项

### 15.2 飞书

- [ ] 飞书应用已开通 `contact:user:readonly` 权限
- [ ] 飞书应用已开通 `im:message`、`im:chat`、`docs:doc` 权限
- [ ] 事件订阅已配置 `im.message.receive_v1`
- [ ] 应用已发布并管理员审批通过

### 15.3 讯飞

- [ ] 实时语音转写服务已开通
- [ ] ISV 声纹识别服务已开通
- [ ] 声纹组已创建（smart_meeting_vp）
- [ ] API Key/Secret 已配置

### 15.4 大模型

- [ ] LLM API Key 已配置
- [ ] LLM API 连通性已验证
- [ ] 纪要生成 Prompt 已调优

### 15.5 安全

- [ ] JWT Secret 已修改为随机强密码
- [ ] DB 密码未硬编码
- [ ] 飞书 App Secret 未硬编码
- [ ] Nginx 已配置 HTTPS

---

*文档结束 - v0.3 - 2026-05-09*  
*变更：v0.2→v0.3 将文档与仓库当前实现对齐——项目树、DDL（chat_id、recording_token、声纹表）、REST 路径（`/api/v1/audio/...`、`/rec/...`）、消息流（RecordingService + 可选 `end`）、Web 静态资源布局、Docker/Nginx 说明、去除 MeetingStateMachine 伪代码、MeetingService/FeishuService 与代码一致。*  
*v0.3 增补：§5.4 待办管理 REST（列表/看板/状态/分配）与 `TodoController` 实现。*
