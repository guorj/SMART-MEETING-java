# PRD - 智能会议系统（Java版）

> 版本：v2.1-java  
> 日期：2026-05-08  
> 作者：阿统 🔧  
> 状态：v2.0基础上的15项查缺补漏修复  
> 参考流程图：三阶段会议管理闭环（会前筹备 → 会中记录 → 会后跟踪 → 回流下一轮）

---

## 1. 产品概述

### 1.1 产品定位

基于 **Java（Spring Boot 3.x）** + 飞书开放平台 + 科大讯飞/腾讯云ASR + 大模型 的智能会议全生命周期管理系统。以飞书机器人作为用户统一入口，覆盖**会前筹备、会中智能记录、会后任务跟踪**三大阶段，形成"会议→待办→跟踪→下次会议回顾"的完整闭环。

### 1.2 核心价值

| 痛点 | 解决方案 |
|------|----------|
| 会前筹备分散：议题收集靠聊天、会议室手动约、资料零散发送 | 飞书对话内一站式完成议题收集、参会人确定、会议室预约、资料推送 |
| 会后整理纪要耗时长（1小时会议需1-2小时整理） | 全自动生成，会议结束5-10分钟内出结果 |
| 会议决议无人跟踪，下次开会发现上次的事都没做 | 自动拆解待办→责任到人→进度提醒→下次会议自动通报完成情况 |
| 手动标注"谁说了什么"容易混淆 | 音色识别自动区分发言人 |
| 纪要格式不统一、待办遗漏 | 大模型生成标准化结构纪要 + 待办自动同步 |

### 1.3 目标用户

- 企业内部团队（线上线下会、项目评审、跨部门协作会议）
- 管理层（需要快速获取会议决策和待办）
- 外部小微企业（提供会议管理闭环解决方案）

### 1.4 核心差异化：三阶段闭环

```
┌──────────────────────────────────────────────────────────────────┐
│                        会议管理全生命周期                           │
│                                                                  │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐               │
│  │  ①会前    │ ───→ │  ②会中    │ ───→ │  ③会后    │               │
│  │  筹备与邀约 │      │  智能记录   │      │  任务跟踪   │               │
│  └──────────┘      └──────────┘      └────┬─────┘               │
│        ↑                                  │                      │
│        └──────── 回流：下次会议通报进度 ────┘                      │
│                                                                  │
│  闭环价值：每次会议都对上次待办有交代，决议事项持续跟进直至落地      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. 业务流程总图（基于流程图）

```
                            ┌─────────┐
                            │   开始   │
                            └────┬────┘
                                 │
                    ┌────────────▼────────────┐
                    │      收集议题              │
                    │  (梳理核心讨论议题)         │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                                     │
    ┌─────────▼─────────┐              ┌────────────▼──────────┐
    │   确定参会人        │              │   会议室预约            │
    │   发送议程          │              │   会议资料推送          │
    │ (对齐讨论框架)      │              │ (上次纪要/业务数据)     │
    └─────────┬─────────┘              └────────────┬──────────┘
              │                                     │
              └──────────────────┬──────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      参会确认              │
                    │  ┌──────────────────┐    │
                    │  │   是否参会?       │    │
                    │  └───┬──────────┬───┘    │
                    └──────┼──────────┼────────┘
                           │否        │是
                           ▼          ▼
              ┌────────────────┐  ┌────────────────┐
              │ 回流至"确定参   │  │   会议邀约       │
              │ 会人"重新调整   │  │ (时间/地点/      │
              │                │  │  议程/链接)      │
              └────────────────┘  └───────┬────────┘
                                          │
                              ╔═══════════╧═══════════╗
                              ║    ② 会中阶段           ║
                              ╚═══════════╤═══════════╝
                                          │
                              ┌───────────▼───────────┐
                              │      会议开始           │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │   上次会议进度通报       │
                              │ (同步决议事项完成情况)   │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │      会议录音           │
                              │ (全程语音记录)          │
                              └───────────┬───────────┘
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    │                                           │
          ┌─────────▼─────────┐                    ┌────────────▼──────────┐
          │   实时录音解析      │                    │   离线录音校正          │
          │ (边录边转文字)      │                    │ (二次校验修正误差，若置信度<0.7,使用大模型根据上下文纠错)      
          └─────────┬─────────┘                    └────────────┬──────────┘
                    │                                           │
                    └─────────────────────┬─────────────────────┘
                                          │
                              ┌───────────▼───────────┐
                              │      发言人识别         │
                              │       (声纹识别)       │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │  生成文字版会议纪要      │
                              └───────────┬───────────┘
                                          │
                              ╔═══════════╧═══════════╗
                              ║    ③ 会后阶段           ║
                              ╚═══════════╤═══════════╝
                                          │
                              ┌───────────▼───────────┐
                              │  同步到责任人待办        │
                              │ (拆解任务→分配→设截止)  │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │      进度提醒           │
                              │ (临期通知/节点提醒)     │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │      进度跟踪           │
                              │ (记录完成/卡点问题)     │
                              └───────────┬───────────┘
                                          │
                              ┌───────────▼───────────┐
                              │  回流至「开始」         │
                              │ (下次会议通报进度)      │
                              └───────────────────────┘
```

---

## 3. 功能需求（按流程图三阶段组织）

### 3.1 会前阶段：筹备与邀约

> **阶段目标**：完成会议议题、人员、场地、资料的全方面筹备，确认参会情况并发起邀约。
> **流程入口**：用户在飞书对话中发送"发起会议"或通过机器人菜单操作。
> **流程出口**：邀约发送成功 → 进入会中阶段；参会人拒绝 → 回流调整。

| 编号 | 功能 | 描述 | 优先级 | 流程图对应节点 |
|------|------|------|--------|---------------|
| F-PRE-01 | 收集议题与参会人拟定 | 发起人预约（可临时预约、定时会议则由系统到时自动发起）会议（会议主题+参会人【此时应有列表显示参会人在时间段空闲状态】+期待开始时间）请求，系统向相应参会人推送会议预约信息，同时接收参会人反馈：是否参会（会议开始前24确认，超时不确认默认参会；2h<当前时间与会议时间间隔<24h,则需会议开始前2h确认；2h>当前时间与会议时间间隔>30min，则需在30分钟内确认；当前时间与会议时间间隔<30min,则发送紧急通知，需要收到后5分钟内确认），参会议题（可不填），预计时长（默认5分钟），是否新增参会人（默认不新增） | P0 | 收集议题、拟定参会人 
| F-PRE-02 | 议题汇总与参会人确认 | 系统汇总议题确认议程、确认新增参会人是否参会 | P0 | 确定参会人 |
| F-PRE-03 | 发送议程 | 向所有参会人推送会议议程（议题列表+讨论框架+预计时长，无论参会人是否参会），参会人可提前预览准备材料 | P0 | 发送议程 |
| F-PRE-04 | 会议室预约 | 对接飞书日历（自动创建日程）/会议室API（二期），自动查询空闲时段并预约；支持线上会议创建（飞书视频会议） | P1 | 会议室预约 |
| F-PRE-05 | 会议资料推送 | 推送会议背景资料：上次会议纪要（含待办完成情况）、相关业务数据、参考文档等；资料以飞书消息附件/链接形式推送 | P1 | 会议资料推送 |
| F-PRE-06 | 参会确认 | 向参会人发送确认请求（飞书卡片消息，一键"参加/请假"），系统统计确认结果并展示确认率 | P0 | 参会确认（判断节点） |
| F-PRE-07 | 参会人调整 | 当有人确认"不参加"时（尤其是关键决策者），系统提示发起人调整参会人名单或二次邀约；支持设置"必须参加"标记 | P0 | 否→回流至"确定参会人" |
| F-PRE-08 | 会议邀约 | 确认完成后发送正式邀约（飞书日程邀请），含时间、地点/线上链接、议程、资料包；同时生成录音页面链接供会中使用 | P0 | 会议邀约 |

#### 3.1.1 会前交互设计

```
用户发送: "发起会议 主题:Q2产品评审 时间:周五14:00"
        或通过飞书机器人菜单选择"发起会议"

系统响应流程:
1. 记录议题 → 生成议题编号（AG-001, AG-002...）
2. 提示用户选择/确认参会人（飞书选人组件）
3. 确认后推送议程给参会人
4. 可选：自动预约会议室（需对接飞书日历API）
5. 可选：推送会议资料（上次纪要+关联文档）
6. 发送参会确认卡片（24h内有效，超时默认参加）
7. 根据确认结果：全部确认→发送正式邀约；部分拒绝→提示调整

状态流转:
[议题收集] → [参会人确定] → [议程推送] → [会议室预约+资料推送]（并行）
→ [参会确认] → [是]→[邀约发送] → 进入会中阶段
              → [否]→[调整参会人]→ 重新确认
```

---

### 3.2 会中阶段：智能记录与纪要生成

> **阶段目标**：保障会议开展，同时通过智能技术完成会议内容的记录、解析与结构化处理，自动生成会议纪要。
> **流程入口**：会议邀约时间到达，用户发送"开始会议"或点击邀约卡片按钮。
> **流程出口**：纪要生成完成 → 进入会后跟踪阶段。

| 编号 | 功能 | 描述 | 优先级 | 流程图对应节点 |
|------|------|------|--------|---------------|
| F-MID-01 | 会议开始 | 承接邀约正式开启会议，系统自动进入记录就绪状态；若关联上次会议，自动拉取待办统计数据 | P0 | 会议开始 |
| F-MID-02 | 上次会议进度通报 | **【新增-流程图关键节点】** 会议开始时，自动展示上次会议待办事项的完成情况统计（已完成X/未完成Y/进行中Z），对延期项高亮标注并请责任人说明原因，保障事项跟进连续性 | P1 | 上次会议进度通报 |
| F-MID-03 | 会议录音启动 | 用户在飞书对话中发送"开始录音"或点击邀约卡片中的录音按钮，系统启动Web录音页面（需HTTPS+麦克风权限） | P0 | 会议录音 |
| F-MID-04 | 实时录音解析 | 边录边转文字：浏览器通过AudioContext采样16kHz单声道PCM，WebSocket每40ms推送一帧（1280字节）至后端ASR引擎，Web页面实时回显转写结果（初稿） | P0 | 实时录音解析 |
| F-MID-05 | 离线录音校正 | 录音结束后对音频文件二次校验：①修正口音/术语识别误差 ②对低置信度片段（confidence<0.7）用大模型做文本级纠错 ③VAD检测信号质量差的片段从本地音频缓存重新识别 | P1 | 离线录音校正 |
| F-MID-06 | 发言人识别 | 基于音色识别大模型（讯飞ISV声纹），通过语音特征区分不同发言人，将speaker_0/1/2映射为实际参会人姓名 | P0 | 发言人识别 |
| F-MID-07 | 生成文字版会议纪要 | 整合实时解析+离线校正+发言人信息，大模型按标准模板生成结构化纪要（议题回顾→讨论要点→决议→待办），写入飞书文档 | P0 | 生成文字版会议纪要 |
| F-MID-08 | 暂停/继续录音 | 支持茶歇时段暂停录音（暂停推流但保持WebSocket连接），节省ASR配额；恢复后自动续传 | P2 | — |
| F-MID-09 | 超时保护 | 最大录音时长4h（超时自动停止并通知参会人）；连续30min无有效语音（VAD静音检测）自动暂停并提醒；每小时推送录音状态提醒（时长/费用预估） | P0 | — |

#### 3.2.1 会中交互设计

```
会议开始时（飞书卡片消息）:
┌──────────────────────────────────────┐
│  📋 Q2产品评审会                       │
│                                       │
│  📊 上次会议待办进度:                   │
│  ✅ 2项已完成  🔄 1项进行中  ❌ 0项未开始 │
│                                       │
│  [🎤 打开录音]  [📄 查看上次纪要]       │
└──────────────────────────────────────┘

录音中（Web录音页面）:
┌──────────────────────────────────────┐
│  🔴 录音中  00:23:45                   │
│                                       │
│  实时转写:                             │
│  👤 张三 (14:30): 今天讨论Q2产品...    │
│  👤 李四 (14:32): 前端完成了...        │
│                                       │
│  💡 结束时在飞书发送"结束会议"自动生成纪要 │
└──────────────────────────────────────┘

结束后（飞书卡片消息）:
┌──────────────────────────────────────┐
│  ✅ 纪要已生成                         │
│                                       │
│  📄 纪要文档: [打开飞书文档]            │
│  📌 待办: 3项（已同步责任人飞书任务）    │
│                                       │
│  🔔 待办跟踪已自动开启                  │
└──────────────────────────────────────┘
```

---

### 3.3 会后阶段：任务落地与循环跟进

> **阶段目标**：将会议决议转化为可执行的待办事项，通过提醒与跟踪保障任务落地，形成会议管理的闭环。
> **流程入口**：纪要生成完成，系统自动拆解待办。
> **流程出口**：下次会议通报完成情况 → 回流至开始。

| 编号 | 功能 | 描述 | 优先级 | 流程图对应节点 |
|------|------|------|--------|---------------|
| F-POST-01 | 同步到责任人待办 | 从纪要中拆解待办任务，分配给对应责任人（通过user_id），明确任务内容、要求与截止时间；同步到飞书待办/任务系统；若声纹已注册则自动匹配人名。<br>**责任人匹配逻辑**：优先通过 `int_meeting_participant.name` → `userId` 匹配；多人同名 → 标注"待确认"，通知发起人手动指定；无法匹配 → `assigneeId` 暂空，记录到"未分配"列表。 | P0 | 同步到责任人待办 |
| F-POST-02 | 进度提醒 | 向责任人发送进度提醒：临近截止通知（截止前24h / 2h）、阶段性节点提醒（如按周迭代）；可配置提醒频率和免打扰时段 | P1 | 进度提醒 |
| F-POST-03 | 进度跟踪 | 持续跟踪待办执行进度：责任人可通过飞书卡片"标记完成"/"申请延期"更新状态；发起人可查看所有待办状态看板（按会议按人统计）；记录完成情况与卡点问题 | P1 | 进度跟踪 |
| F-POST-04 | 回流至下次会议 | **【闭环关键】** 下次会议开场时，通过 previousMeetingId 自动关联，统计并通报上次会议待办完成情况（已完成/进行中/未开始/已延期），形成持续跟进循环 | P1 | 回流至「开始」 |
| F-POST-05 | 待办状态看板 | 提供会议维度+人员维度双视图待办看板：本次会议全部待办、状态分布饼图、延期项红色高亮标记 | P2 | — |
| F-POST-06 | 手动修正 | 用户可在飞书文档中手动修正说话人标记错误、待办分配错误，触发纪要重新生成（不重新录音，仅重跑大模型+文档替换） | P2 | — |

#### 3.3.1 会后闭环机制设计

```
会后系统自动化流程:

会议结束 → 生成纪要 → 拆解待办 → 分配责任人 → 同步飞书任务
    │                                                       │
    │    ┌──────────────────────────────────────────────────┘
    │    ▼
    │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
    │  │  进度提醒      │    │  进度跟踪      │    │  待办看板      │
    │  │ · 临期:24h/2h  │    │ · 责任人更新    │    │ · 完成率统计   │
    │  │ · 节点提醒     │    │ · 卡点记录      │    │ · 延期标记     │
    │  │ · 免打扰时段   │    │ · 升级通知      │    │ · 个人/会议维度 │
    │  └──────────────┘    └──────────────┘    └──────────────┘
    │
    └──────────→ 下次会议开场 → 自动通报上次待办完成情况
                    ↓
              "[系统] 上次会议共5项待办：✅3项已完成 🔄1项进行中 ❌1项延期
               延期项: Q2前端重构(张三)，请说明原因"
```

---

## 4. 系统架构（Java技术栈）

### 4.1 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| **后端框架** | Spring Boot 3.x + JDK 17+ | 企业级Java生态，成熟稳定；GraalVM可选做AOT编译 |
| **Web框架** | Spring WebFlux (Netty) | 响应式非阻塞I/O，原生WebSocket支持，高并发场景优于Tomcat |
| **ORM** | MyBatis-Plus 3.5+ | 灵活的SQL控制+代码生成器；JSON字段自动映射 |
| **数据库** | MySQL 8.0 (InnoDB) + Redis 7 | MySQL存结构化业务数据；Redis缓存飞书token/会议状态/WebSocket session |
| **消息队列** | Kafka | 异步解耦（纪要生成、待办提取），高吞吐+持久化+分区有序；延时提醒用Redis ZSet |
| **定时任务** | XXL-Job 2.x | 分布式任务调度，可视化管理；替代Spring @Scheduled（单机限制） |
| **API文档** | SpringDoc OpenAPI 3.0 | 自动生成Swagger UI，开发阶段联调 |
| **配置中心** | Nacos 2.x | 多环境配置管理+服务注册发现（如后续微服务化） |
| **前端（录音页）** | 原生HTML/JS/CSS | 轻量零依赖，独立部署于Nginx，与后端WebSocket直连 |
| **ASR（主）** | 科大讯飞实时语音转写 + ISV声纹识别 | 声纹注册+说话人分离+实时转写一体化，准确率≥95% |
| **ASR（备）** | 腾讯云实时语音识别 | 降级备选方案，纯话者分离模式 |
| **大模型** | 可配置（豆包/通义千问/DeepSeek） | 纪要生成、待办提取、文本纠错；通过统一LLM Gateway抽象 |
| **部署** | Docker + Docker Compose | 容器化部署，环境统一；资源限制防止内存泄漏影响宿主机 |

### 4.2 模块架构

```
smart-meeting-java/
├── meeting-server/                        # 后端主服务 (Spring Boot 3.x 多模块Maven项目)
│   ├── meeting-server-api/                # 【API接口层】对外暴露REST API + WebSocket端点
│   │   ├── controller/
│   │   │   ├── MeetingController.java     # 会议CRUD + 全生命周期管理（创建/推进/查询/取消）
│   │   │   ├── AudioController.java       # 音频WebSocket端点（连接管理/推流转发/状态同步）
│   │   │   ├── MinuteController.java      # 纪要查询/手动修正/重新生成/历史检索
│   │   │   ├── TodoController.java        # 待办管理（查看/更新状态/看板/关联下次会议）
│   │   │   └── VoiceprintController.java  # 声纹注册/查询状态/批量预加载
│   │   ├── dto/                           # 数据传输对象（入参Request/出参Response）
│   │   │   ├── request/                   # 请求DTO
│   │   │   └── response/                  # 响应DTO（含Swagger注解）
│   │   └── config/
│   │       └── WebFluxConfig.java         # WebFlux路由/CORS/静态资源配置
│   │
│   ├── meeting-server-service/            # 【业务逻辑层】核心业务编排
│   │   ├── meeting/                       # 会议管理模块
│   │   │   ├── MeetingService.java        # 会议生命周期编排（创建→推进→结束的完整流程）
│   │   │   ├── MeetingStateMachine.java   # 会议状态机（状态+事件→新状态，保证状态流转合法性）
│   │   │   └── ParticipantService.java    # 参会人管理（增删/确认/声纹关联）
│   │   ├── preparation/                   # 【会前筹备-流程图新增】
│   │   │   ├── AgendaService.java         # 议题CRUD + 排序 + 跨会议议题复用
│   │   │   ├── RoomBookingService.java    # 会议室查询/预约/释放（对接飞书日历API）
│   │   │   ├── MaterialPushService.java   # 资料收集（上次纪要/关联文档）+ 飞书消息推送
│   │   │   ├── AttendanceService.java     # 参会确认（超时自动确认/确认率统计/回流建议）
│   │   │   └── InvitationService.java     # 邀约生成（飞书日程+卡片消息+录音链接嵌入）
│   │   ├── recording/                     # 会中录音模块
│   │   │   ├── AudioBridgeService.java    # WebSocket双向桥接（浏览器↔后端↔ASR引擎）
│   │   │   ├── AudioCacheService.java     # 音频分片缓存（按会议ID+时间戳分片，支持断点续传）
│   │   │   └── RecordingStateService.java # 录音状态管理（暂停/继续/超时/VAD静音检测）
│   │   ├── asr/                           # ASR引擎适配模块
│   │   │   ├── XfyunAsrClient.java        # 讯飞实时ASR WebSocket客户端（鉴权+建连+解析）
│   │   │   ├── TencentAsrClient.java      # 腾讯云ASR客户端（备选降级方案）
│   │   │   ├── AsrOrchestrator.java       # ASR调度器（主备切换/指数退避重连/健康检查）
│   │   │   └── OfflineCorrectionService.java # 离线校正（低置信度片段→大模型纠错+信号质量重识别）
│   │   ├── voiceprint/                    # 声纹识别模块
│   │   │   ├── IsvClient.java             # 讯飞ISV API客户端（HMAC-SHA256鉴权）
│   │   │   ├── VoiceprintService.java     # 声纹注册/过期检测/自动续期提醒
│   │   │   └── SpeakerIdentification.java # 会后说话人识别（PCM提取→ISV搜索→姓名映射）
│   │   ├── minute/                        # 纪要生成模块
│   │   │   ├── MinuteGenerationService.java # 大模型纪要生成（Prompt组装+调用+JSON容错解析）
│   │   │   ├── PromptTemplateService.java   # Prompt模板管理（支持A/B测试、版本管理）
│   │   │   └── TodoExtractionService.java   # 待办提取（从纪要文本中识别action item+责任人+截止时间）
│   │   ├── todo/                          # 【会后待办跟踪-流程图新增】
│   │   │   ├── TodoSyncService.java       # 待办同步飞书任务（创建任务+分配+设截止+双向状态同步）
│   │   │   ├── ProgressReminderService.java # 进度提醒（延时消息触发/临期梯度提醒/免打扰判断）
│   │   │   ├── ProgressTrackService.java  # 进度跟踪（状态变更/卡点记录/延期自动标记/升级通知）
│   │   │   └── TodoBoardService.java      # 待办看板（会议维度/人员维度/状态分布/延期告警）
│   │   ├── review/                        # 【上次会议回顾-流程图新增】
│   │   │   └── PreviousMeetingService.java # 上次会议进度通报（查询→统计→格式化→飞书卡片推送）
│   │   └── feishu/                        # 飞书开放平台对接模块
│   │       ├── FeishuAuthService.java     # 飞书鉴权（tenant_access_token缓存+自动刷新+重试）
│   │       ├── FeishuMessageService.java  # 消息/卡片发送（支持文本/卡片/富文本/文件）
│   │       ├── FeishuDocService.java      # 文档创建/写入/权限设置（Block结构化写入）
│   │       ├── FeishuCalendarService.java # 日历/会议室API（查询空闲/创建日程/预约会议室）
│   │       ├── FeishuTaskService.java     # 飞书任务API（创建/更新/完成/提醒设置）
│   │       └── FeishuEventService.java    # 事件订阅处理（消息事件/卡片回调/日程变更）
│   │
│   ├── meeting-server-infra/              # 【基础设施层】数据访问+中间件+公共组件
│   │   ├── config/
│   │   │   ├── WebSocketConfig.java       # WebSocket配置（端点注册/握手拦截器/最大帧大小）
│   │   │   ├── KafkaConfig.java           # Kafka配置（生产者/消费者/序列化/Topic绑定）
│   │   │   ├── SchedulerConfig.java       # XXL-Job执行器配置（注册中心/任务扫描/日志）
│   │   │   └── SecurityConfig.java        # Spring Security配置（JWT+飞书token双重验证）
│   │   ├── repository/
│   │   │   ├── MeetingRepository.java     # 会议Mapper（含状态流转SQL/待办统计SQL/历史查询）
│   │   │   ├── TodoRepository.java        # 待办Mapper（按会议/按人/按状态/延期查询）
│   │   │   ├── VoiceprintRepository.java  # 声纹Mapper（按userId/按过期时间查询）
│   │   │   └── UserMappingRepository.java # 用户映射Mapper（userId↔feishuUserId双向查询）
│   │   ├── mq/
│   │   │   ├── KafkaProducerService.java  # Kafka生产者（发送+回调+失败重试）
│   │   │   └── consumer/                  # Kafka消费者
│   │   │       ├── MinuteGenerateConsumer.java  # 纪要生成消费者（校正→识别→生成→写文档→发todo.extract）
│   │   │       └── TodoExtractConsumer.java     # 待办提取消费者（纪要文本→结构化待办）
│   │   └── common/
│   │       ├── exception/                 # 全局异常处理（@RestControllerAdvice + 业务异常枚举）
│   │       │   ├── GlobalExceptionHandler.java
│   │       │   └── BusinessException.java
│   │       ├── enums/                     # 枚举定义（MeetingStatus/ConfirmStatus/TodoStatus/Priority/RemindType）
│   │       ├── aspect/
│   │       │   └── AuditLogAspect.java    # 审计日志AOP（记录关键操作的时间/人/内容/结果）
│   │       └── util/                      # 工具类（HMAC签名/音频格式转换/时间格式化）
│   │
│   ├── pom.xml                            # Maven父POM（依赖版本统一管理）
│   └── application.yml                    # 主配置文件（详见附录B）
│
├── meeting-server/src/main/resources/static/  # 录音前端（唯一维护；打包进 classpath）
│   ├── index.html                         # 录音页面
│   ├── start-meeting.html                 # 飞书 Web 选会页
│   ├── recorder.js                        # 录音核心（AudioWorklet→PCM→WebSocket）
│   ├── styles/main.css                    # 样式
│   └── worklet/pcm-processor.js           # AudioWorklet
│
├── sql/                                   # 手工/增量迁移（全量建表 DDL 仅维护 meeting-server/src/main/resources/schema.sql）
│   └── migration_*.sql
│
├── docker-compose.yml                     # 容器编排（后端+MySQL+Redis+Kafka+Nginx+Nacos）
└── README.md                              # 项目说明（快速启动/环境依赖/开发指南）
```

### 4.3 核心状态机（会议全生命周期）

```
                                  会前阶段
  ┌─────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
  │ 议题收集 │ → │ 参会人确定 │ → │ 议程已发送 │ → │ 参会确认中 │ → │  已邀约   │
  │ ISSUE   │    │ ATTENDEE │    │ AGENDA  │    │ CONFIRM │    │ INVITED │
  └─────────┘    └──────────┘    └──────────┘    └────┬─────┘    └──────────┘
                                                      │
                                        ┌─────────────┴─────────────┐
                                  全部确认│                           │部分拒绝
                                        ▼                           ▼
                                  ┌──────────┐              ┌──────────┐
                                  │  已邀约   │              │ 调整参会人 │
                                  └────┬─────┘              └──────────┘
                                       │                         │
                            会中阶段    │                         │
  ┌─────────┐    ┌──────────┐    ┌────▼─────┐                    │
  │  会议开始 │ → │ 进度通报  │ → │  录音中   │
  │ STARTED │    │ REVIEW  │    │RECORDING │
  └─────────┘    └──────────┘    └────┬─────┘
                                      │
                              ┌───────┴───────┐
                         正常结束│               │超时/异常
                              ▼               ▼
                        ┌──────────┐    ┌──────────┐
                        │  处理中   │    │ 已异常终止 │
                        │PROCESSING│    │ ABORTED  │
                        └────┬─────┘    └──────────┘
                             │
                             ▼
                        ┌──────────┐
                        │  已完成   │
                        │COMPLETED │
                        └────┬─────┘
                             │
                                  会后阶段
  ┌──────────────┐    ┌──────────┐    ┌──────────┐
  │  待办跟踪中    │ → │  全部完成  │ → │  已归档   │
  │ TODO_TRACKING│    │ ALL_DONE │    │ ARCHIVED │
  └──────────────┘    └────┬─────┘    └──────────┘
                           │
                回流至下次"进度通报"
                (自动统计完成情况)
```

> **状态说明**：会后阶段仅 `TODO_TRACKING`（待办跟踪中，含进行中/延期/未开始）→ `ALL_DONE`（全部完成或取消），不再有独立的"进度跟踪中"状态，消除 TRACKING 歧义。

### 4.3.1 状态机流转规则表

| # | 当前状态 | 事件/操作 | 新状态 | 触发条件 | 业务动作 |
|---|---------|----------|--------|----------|----------|
| 1 | ISSUE_COLLECTING | 发起人添加议题 | ISSUE_COLLECTING | 任意 | 追加议题到agenda JSON |
| 2 | ISSUE_COLLECTING | 发起人确定参会人 | ATTENDEE_CONFIRMING | 至少1个议题+1个参会人 | 推送参会确认卡片 |
| 3 | ATTENDEE_CONFIRMING | 参会人确认参加 | ATTENDEE_CONFIRMING | 至少1人未确认 | 更新确认状态，计算确认率 |
| 4 | ATTENDEE_CONFIRMING | 参会人请假 | ATTENDEE_CONFIRMING | 非关键决策者 | 标记DECLINED，通知发起人 |
| 5 | ATTENDEE_CONFIRMING | 全部确认参加 | AGENDA_SENT | 所有required=ture的参会人均CONFIRMED | 自动推送议程 |
| 6 | ATTENDEE_CONFIRMING | 关键人员拒绝 | ATTENDEE_CONFIRMING | required=true的参会人DECLINED | 提示发起人调整参会人 |
| 7 | AGENDA_SENT | 发起人手动推进 | INVITED | 确认率≥80% | 发送正式邀约+日程 |
| 8 | AGENDA_SENT | 超时自动确认 | INVITED | 24h未响应 | TIMEOUT_CONFIRMED，发送邀约 |
| 9 | INVITED | 会议时间到达 | STARTED | 定时任务检测 | 推送上次会议进度通报卡片 |
| 10 | INVITED | 发起人手动开始 | STARTED | 任意 | 推送上次会议进度通报卡片 |
| 11 | STARTED | 进度通报完成 | REVIEWING | 自动流转 | 展示上次待办统计 |
| 12 | REVIEWING | 发起人点击开始录音 | RECORDING | 任意 | 生成录音链接+JWT token |
| 13 | RECORDING | 发起人暂停 | PAUSED | 任意 | 暂停推流，保持WebSocket连接 |
| 14 | PAUSED | 发起人继续 | RECORDING | 任意 | 恢复推流 |
| 15 | RECORDING/PAUSED | 发起人结束录音 | PROCESSING | 任意 | 关闭WebSocket+ASR，发送meeting.events消息 |
| 16 | RECORDING | 超时自动停止(4h) | PROCESSING | 录制>4h | 自动停止+通知，发送meeting.events消息 |
| 17 | PROCESSING | 纪要生成完成 | COMPLETED | minute-generate消费完成 | 创建飞书文档，发送todo.extract消息 |
| 18 | COMPLETED | 待办提取完成 | TODO_TRACKING | todo-extract消费完成 | 待办同步飞书任务 |
| 19 | TODO_TRACKING | 全部待办完成/取消 | ALL_DONE | 所有todo status=COMPLETED/CANCELLED | 会议归档准备 |
| 20 | TODO_TRACKING/ALL_DONE | 发起人归档 | ARCHIVED | 手动操作 | 锁定会议，仅可查看 |

### 4.3.2 previousMeetingId 自动关联逻辑

当发起人创建新会议时，系统自动查找并关联上次会议ID：

1. **查找规则**：在同一 `group_name` + `company` + `department` 范围内，查找状态为 `COMPLETED` 或 `ARCHIVED` 的最新会议（按 `actual_end_time DESC` 排序，取第一条）
2. **关联条件**：
   - 必须同属一个会议组（group_name相同）
   - 必须同属一个集团（company相同）
   - 部门字段匹配（department相同，若均为NULL则匹配）
   - 上次会议必须有actual_end_time（已结束）
3. **首次会议**：若未找到匹配的已完成/已归档会议，`previousMeetingId` 保持 NULL
4. **用途**：新会议 STARTED 时，通过 previousMeetingId 查询上次待办完成情况并推送通报卡片

---

## 5. 核心数据模型（Java实体）

> **设计原则**：每个字段均有 JavaDoc + @Column(columnDefinition) 注释，确保代码自文档化。
> **命名规范**：数据库字段用 snake_case，Java属性用 camelCase，MyBatis-Plus自动映射。
> **ID体系统一**：所有用户标识使用 `user_id`（飞书企业内唯一ID，通过 `int_user_mapping` 表与OA系统关联）。

### 5.1 会议实体

```java
/**
 * 会议核心实体，贯穿会前→会中→会后全生命周期。
 *
 * 职责：
 * - 状态字段驱动业务流程编排（状态机保证流转合法性）
 * - previousMeetingId 实现会议间闭环关联（上次←本次→下次）
 * - 存储录音/纪要/文档等关键资源的访问凭证
 *
 * @see MeetingStatus 会议状态枚举
 */
@Data
@Table(name = "int_meeting", comment = "会议主表，记录每次会议的全生命周期信息")
public class Meeting {

    // ==================== 唯一标识 ====================

    /** 会议唯一标识，UUID v4 生成（36字符），在飞书消息/卡片中作为会议ID展示 */
    @TableId
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) COMMENT '会议唯一标识，UUID v4生成'")
    private String id;

    // ==================== 基本信息 ====================

    /** 会议主题/标题，如"Q2产品评审会"，飞书卡片头部和地方标题使用 */
    @Column(length = 200, nullable = false, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '会议主题/标题'")
    private String title;

    /**
     * 议题列表，JSON数组格式。
     * 示例：[{"no":1,"topic":"Q2产品规划","duration":15,"submitter":"张三"},
     *        {"no":2,"topic":"技术方案讨论","duration":20,"submitter":"李四"}]
     */
    @Column(columnDefinition = "JSON COMMENT '议题列表（JSON数组），字段：no编号/topic标题/duration预计时长(分钟)/submitter提交人'")
    private String agenda;

    /** 所属集团，如"集团"、"艾科森"等 */
    @Column(length = 200, nullable = false, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '所属集团'")
    private String company;

    /** 集团部门，没有可不填 */
    @Column(length = 200, columnDefinition = "VARCHAR(200) COMMENT '集团部门'")
    private String department;

    /** 会议组，如"经管会"、"技术委员会"、"周例会" */
    @Column(length = 200, nullable = false, columnDefinition = "VARCHAR(200) NOT NULL COMMENT '会议组'")
    private String groupName;

    // ==================== 状态驱动 ====================

    /**
     * 会议当前状态，驱动全生命周期业务流程流转。
     * 状态变迁必须通过 MeetingStateMachine 执行，确保合法性。
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false, columnDefinition = "VARCHAR(30) NOT NULL COMMENT '会议状态：ISSUE_COLLECTING/ATTENDEE_CONFIRMING/AGENDA_SENT/CONFIRMING/INVITED/STARTED/REVIEWING/RECORDING/PAUSED/PROCESSING/COMPLETED/TODO_TRACKING/ALL_DONE/ARCHIVED/ABORTED/CANCELLED'")
    private MeetingStatus status;

    // ==================== 人员信息 ====================

    /** 会议发起人飞书 user_id，拥有会议管理权限（修改基本信息/手动推进流程/取消会议） */
    @Column(length = 64, nullable = false, columnDefinition = "VARCHAR(64) NOT NULL COMMENT '会议发起人飞书user_id'")
    private String creatorId;

    // ==================== 场地/线上会议 ====================

    /** 会议室ID（线下实体会议室）或飞书视频会议ID（线上），由 RoomBookingService 通过飞书日历API获取 */
    @Column(length = 64, columnDefinition = "VARCHAR(64) COMMENT '会议室ID（线下）或飞书视频会议ID（线上）'")
    private String roomId;

    // ==================== 闭环关联（流程图核心概念） ====================

    /**
     * 上次会议ID，实现"回流至开始"的闭环关键字段。
     * 用途：下次会议开场时，通过此字段查询上次会议的待办完成情况并通报。
     * 首次会议（无上次会议）时此字段为 NULL。
     */
    @Column(length = 36, columnDefinition = "VARCHAR(36) COMMENT '上次会议ID，用于闭环通报上次待办进度；首次会议为NULL'")
    private String previousMeetingId;

    // ==================== 时间信息 ====================

    /** 会议预定时间，发起人创建会议时设定，用于飞书日程邀请 */
    @Column(columnDefinition = "DATETIME COMMENT '会议预定时间'")
    private LocalDateTime scheduledTime;

    /** 实际开始时间，用户点击"开始录音"或发送"开始会议"指令时记录 */
    @Column(columnDefinition = "DATETIME COMMENT '会议实际开始时间'")
    private LocalDateTime actualStartTime;

    /** 实际结束时间，用户发送"结束会议"或系统超时自动结束时记录 */
    @Column(columnDefinition = "DATETIME COMMENT '会议实际结束时间'")
    private LocalDateTime actualEndTime;

    /** 录音总时长（秒），由 actualEndTime - actualStartTime 计算或从音频文件元数据提取 */
    @Column(columnDefinition = "INT COMMENT '录音总时长（秒）'")
    private Integer durationSeconds;

    // ==================== 音频存储 ====================

    /**
     * 音频文件服务器本地缓存路径。
     * 格式：/data/audio/{yyyyMMdd}/{meetingId}.pcm
     * 用于离线校正和会后重新提取音频片段。超过保留期后由定时任务清理。
     */
    @Column(length = 500, columnDefinition = "VARCHAR(500) COMMENT '音频文件本地缓存路径，格式：/data/audio/{date}/{meetingId}.pcm'")
    private String audioPath;

    // ==================== 飞书文档 ====================

    /** 飞书纪要文档访问URL，纪要生成后通过卡片消息返回给用户点击访问 */
    @Column(length = 500, columnDefinition = "VARCHAR(500) COMMENT '飞书纪要文档访问URL'")
    private String docUrl;

    /** 飞书文档唯一 token，用于调用飞书文档API进行内容写入/更新/权限设置 */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '飞书文档唯一token，用于API操作'")
    private String docToken;

    // ==================== 录音页面 ====================

    /**
     * Web录音页面完整URL。
     * 格式：https://{domain}/recorder/{meetingId}?token={oneTimeToken}
     * 其中 token 为一次性鉴权凭证，页面关闭即失效。
     */
    @Column(length = 500, columnDefinition = "VARCHAR(500) COMMENT 'Web录音页面完整URL，含一次性鉴权token'")
    private String recordingUrl;

    /** 录音页面一次性鉴权 token，JWT格式，有效期4h（与最大录音时长一致），由 MeetingAuthService 生成和验证 */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '录音页面一次性鉴权token（JWT），有效期4h'")
    private String recordingToken;

    // ==================== 审计字段 ====================

    /** 记录创建时间，数据库自动填充 */
    @Column(columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间'")
    private LocalDateTime createdAt;

    /** 记录最后更新时间，每次UPDATE时数据库自动刷新 */
    @Column(columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间'")
    private LocalDateTime updatedAt;
}
```

### 5.2 参会人实体

```java
/**
 * 参会人实体，记录每个会议的参与人员及其声纹/确认/待办信息。
 *
 * 职责：
 * - 参会确认（PENDING → CONFIRMED/DECLINED）
 * - 声纹关联（featureId → 会后说话人识别）
 * - 待办统计（聚合该参会人在本次会议的待办完成情况）
 */
@Data
@Table(name = "int_meeting_participant", comment = "会议参会人表，记录参会确认/声纹/待办统计")
public class Participant {

    /** 参会人记录唯一标识，UUID */
    @TableId
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) COMMENT '记录唯一标识，UUID'")
    private String id;

    /** 所属会议ID，关联 int_meeting.id */
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) NOT NULL COMMENT '所属会议ID，外键关联int_meeting.id'")
    private String meetingId;

    /** 飞书用户唯一标识（user_id），用于消息推送、日历邀请、任务分配 */
    @Column(length = 64, nullable = false, columnDefinition = "VARCHAR(64) NOT NULL COMMENT '飞书用户user_id'")
    private String userId;

    /** 用户姓名，用于卡片展示和纪要标注 */
    @Column(length = 100, nullable = false, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '参会人姓名'")
    private String name;

    // ==================== 参会确认 ====================

    /**
     * 参会确认状态。
     * PENDING：待确认（卡片已发，等待用户点击）
     * CONFIRMED：确认参加
     * DECLINED：请假不参加
     * TIMEOUT_CONFIRMED：超时未响应，系统默认参加
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '确认状态：PENDING(待确认)/CONFIRMED(参加)/DECLINED(请假)/TIMEOUT_CONFIRMED(超时默认参加)'")
    private ConfirmStatus status;

    // ==================== 声纹关联 ====================

    /**
     * 讯飞ISV声纹特征ID。
     * 用户完成声纹注册后由讯飞API返回，24h有效。
     * 会后说话人识别时，通过此ID在ISV声纹组中搜索匹配。
     * NULL 表示该用户未注册声纹，转为盲分模式。
     */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '讯飞ISV声纹特征ID，24h有效；NULL表示未注册声纹'")
    private String featureId;

    /**
     * 声纹是否就绪（已注册且未过期）。
     * 会前检测：若 false，向用户推送声纹注册提醒。
     * 会中降级：若 false，该用户的语音用盲分编号标记。
     */
    @Column(columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '声纹是否就绪（1=已注册且未过期，0=未注册或已过期）'")
    private Boolean voiceprintReady;

    // ==================== 待办统计（冗余字段，加速查询） ====================

    /**
     * 该参会人在本次会议中分配的待办总数。
     * 由待办同步服务在拆解后更新，用于参会人列表快速展示。
     */
    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '本次会议分配的待办总数'")
    private Integer todoCount;

    /**
     * 该参会人在本次会议中已完成的待办数。
     * 由进度跟踪服务在状态变更时更新，用于完成率计算。
     */
    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '本次会议已完成的待办数'")
    private Integer completedCount;
}
```

### 5.3 转录分段实体

```java
/**
 * 转录分段实体，记录会议语音转文字的每一句话。
 *
 * 职责：
 * - 存储ASR实时/离线转写结果
 * - 关联说话人信息（声纹匹配前为speaker_N，匹配后为真实姓名）
 * - 记录置信度和校正状态，为离线校正提供输入
 */
@Data
@Table(name = "int_transcript_segment", comment = "转录分段表，记录每句话的识别结果和说话人信息")
public class TranscriptSegment {

    /** 分段唯一标识，UUID */
    @TableId
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) COMMENT '分段唯一标识，UUID'")
    private String id;

    /** 所属会议ID，关联 int_meeting.id */
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) NOT NULL COMMENT '所属会议ID，外键关联int_meeting.id'")
    private String meetingId;

    /**
     * 说话人标识符。
     * 声纹匹配前：speaker_0 / speaker_1 / speaker_2（ASR盲分编号）
     * 声纹匹配后：更新为声纹对应的姓名
     */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '说话人标识（匹配前为speaker_N编号，匹配后为真实姓名）'")
    private String speakerId;

    /** 说话人显示名称，用于飞书文档中的发言人标注（如"张三"） */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '说话人显示名称'")
    private String speakerName;

    /**
     * 该段语音在会议录音中的开始时间偏移（毫秒）。
     * 用于多路音频的时间戳对齐和纪要中标注发言时间。
     */
    @Column(nullable = false, columnDefinition = "INT NOT NULL COMMENT '语音开始时间偏移（毫秒）'")
    private Integer startTimeMs;

    /** 该段语音在会议录音中的结束时间偏移（毫秒） */
    @Column(nullable = false, columnDefinition = "INT NOT NULL COMMENT '语音结束时间偏移（毫秒）'")
    private Integer endTimeMs;

    /** ASR识别的文字内容，原始转写结果或校正后的文本 */
    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '识别/校正后的文字内容'")
    private String text;

    /**
     * 是否为最终结果。
     * 实时ASR中：false=中间结果（会覆盖更新），true=最终结果（不再变化）
     * 离线ASR中：始终为true
     */
    @Column(columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否为最终结果（0=中间结果，1=最终结果）'")
    private Boolean isFinal;

    /**
     * ASR识别置信度，取值范围 0.0 ~ 1.0。
     * 用于离线校正判断：confidence < 0.7 的片段触发大模型文本纠错。
     */
    @Column(columnDefinition = "DOUBLE COMMENT 'ASR识别置信度（0.0-1.0），用于判断是否需要校正'")
    private Double confidence;

    /**
     * 是否已经过离线校正。
     * true：已校正（text字段为校正后文本）
     * false：未校正（text字段为原始ASR结果）
     */
    @Column(columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否已校正（0=原始结果，1=已校正）'")
    private Boolean corrected;
}
```

### 5.4 待办实体（会后跟踪核心）

```java
/**
 * 待办事项实体，是"会后阶段"的核心数据模型。
 *
 * 职责：
 * - 记录从会议纪要中拆解出的每个行动项
 * - 驱动进度提醒（临期通知/节点提醒）
 * - 支撑进度跟踪（状态变更/延期标记/升级通知）
 * - 通过 nextMeetingId 实现"回流至下次会议"
 *
 * 状态流转：PENDING → IN_PROGRESS → COMPLETED
 *                 ↓                   ↓
 *              DELAYED → （重新设截止）→ IN_PROGRESS
 *                 ↓
 *              CANCELLED
 */
@Data
@Table(name = "int_meeting_todo", comment = "会议待办表，记录拆解后的行动项及跟踪信息")
public class MeetingTodo {

    // ==================== 唯一标识 ====================

    /** 待办唯一标识，UUID */
    @TableId
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) COMMENT '待办唯一标识，UUID'")
    private String id;

    // ==================== 关联信息 ====================

    /** 来源会议ID，关联 int_meeting.id */
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) NOT NULL COMMENT '来源会议ID，外键关联int_meeting.id'")
    private String meetingId;

    // ==================== 任务内容 ====================

    /** 待办内容描述，由大模型从纪要中提取，或由发起人手动添加 */
    @Column(nullable = false, columnDefinition = "TEXT NOT NULL COMMENT '待办内容描述'")
    private String content;

    // ==================== 责任人 ====================

    /** 责任人飞书 user_id，用于任务分配和消息提醒推送 */
    @Column(length = 64, nullable = false, columnDefinition = "VARCHAR(64) NOT NULL COMMENT '责任人飞书user_id'")
    private String assigneeId;

    /** 责任人姓名，用于卡片展示和看板统计 */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '责任人姓名'")
    private String assigneeName;

    // ==================== 状态与优先级 ====================

    /**
     * 待办当前状态。
     * PENDING：待开始（默认初始状态）
     * IN_PROGRESS：进行中（责任人确认开始）
     * COMPLETED：已完成（责任人点击"标记完成"）
     * DELAYED：已延期（超过截止时间未完成，系统自动标记）
     * CANCELLED：已取消（发起人取消该待办）
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '待办状态：PENDING(待开始)/IN_PROGRESS(进行中)/COMPLETED(已完成)/DELAYED(已延期)/CANCELLED(已取消)'")
    private TodoStatus status;

    /**
     * 优先级。
     * HIGH：高优先级（必须在截止前完成，延期自动升级通知发起人）
     * MEDIUM：中优先级（默认）
     * LOW：低优先度（提醒频率降低）
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'MEDIUM' COMMENT '优先级：HIGH(高)/MEDIUM(中)/LOW(低)'")
    private Priority priority;

    // ==================== 截止与完成 ====================

    /** 任务截止时间，用于计算提醒触发点和延期判断 */
    @Column(columnDefinition = "DATETIME COMMENT '任务截止时间'")
    private LocalDateTime deadline;

    /** 实际完成时间，责任人点击"标记完成"时记录 */
    @Column(columnDefinition = "DATETIME COMMENT '实际完成时间'")
    private LocalDateTime completedAt;

    /** 完成说明，责任人在标记完成时可选的文字说明 */
    @Column(columnDefinition = "TEXT COMMENT '完成说明（责任人填写）'")
    private String completionNote;

    /** 卡点/延期原因，责任人延期时填写，用于下次会议通报 */
    @Column(columnDefinition = "TEXT COMMENT '卡点/延期原因（责任人填写），用于下次会议通报'")
    private String blockReason;

    // ==================== 提醒跟踪 ====================

    /**
     * 上次提醒发送时间。
     * 用于免打扰判断：距离上次提醒 < 2h 不重复提醒。
     */
    @Column(columnDefinition = "DATETIME COMMENT '上次提醒发送时间'")
    private LocalDateTime lastRemindAt;

    /** 累计提醒次数，用于判断是否达到最大提醒阈值（超过阈值升级通知发起人） */
    @Column(columnDefinition = "INT DEFAULT 0 COMMENT '累计提醒次数'")
    private Integer remindCount;

    // ==================== 闭环关联（流程图核心概念） ====================

    /**
     * 关联的下次会议ID（待办将在该会议开场时通报完成情况）。
     * 由发起人创建新会议时系统自动关联（将上次未完成的待办挂到下次会议）。
     * 若在下次会议前已完成，此字段可能为 NULL。
     */
    @Column(length = 36, columnDefinition = "VARCHAR(36) COMMENT '关联的下次会议ID（待办将在该会议通报）'")
    private String nextMeetingId;

    /**
     * 是否已在下次会议中通报过。
     * 防止重复通报：通报后设置为 true。
     */
    @Column(columnDefinition = "TINYINT(1) DEFAULT 0 COMMENT '是否已在下次会议通报（0=未通报，1=已通报）'")
    private Boolean reportedInNext;

    // ==================== 审计字段 ====================

    /** 待办创建时间，数据库自动填充 */
    @Column(columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '待办创建时间'")
    private LocalDateTime createdAt;
}
```

### 5.5 声纹实体

```java
/**
 * 声纹实体，存储用户注册的声纹特征信息。
 *
 * 职责：
 * - 关联讯飞ISV声纹组的 feature_id
 * - 管理声纹生命周期（注册/过期/续期提醒）
 * - 为会后说话人识别提供 identity → name 的映射
 * - 通过 user_id 关联 OA 系统用户（int_user_mapping）
 */
@Data
@Table(name = "int_voiceprint", comment = "声纹表，存储用户注册的声纹特征信息")
public class Voiceprint {

    /** 记录唯一标识，UUID */
    @TableId
    @Column(length = 36, nullable = false, columnDefinition = "VARCHAR(36) COMMENT '记录唯一标识，UUID'")
    private String id;

    /**
     * OA系统用户ID（关联 system_users 表），唯一索引。
     * 一个用户只能注册一个声纹（如重新注册，覆盖旧记录）。
     */
    @Column(nullable = false, unique = true, columnDefinition = "INT NOT NULL UNIQUE COMMENT 'OA系统用户ID，通过int_user_mapping关联飞书user_id'")
    private Integer userId;

    /** 用户姓名，注册时填写，用于说话人识别后的名称标注 */
    @Column(length = 100, nullable = false, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '用户姓名'")
    private String userName;

    /**
     * 飞书 user_id。
     * 注册声纹时可不填写，后续从 int_user_mapping 表中根据 userId 映射获取。
     */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '飞书用户user_id，可从int_user_mapping表映射获取'")
    private String feishuUserId;

    /**
     * 讯飞ISV声纹特征ID。
     * 由讯飞声纹注册API返回，用于会后在声纹组中搜索匹配说话人。
     */
    @Column(length = 100, nullable = false, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '讯飞ISV声纹特征ID'")
    private String featureId;

    /**
     * 讯飞ISV声纹组ID。
     * 所有用户注册到同一个声纹组，会后识别时在此组内做 1:N 搜索。
     */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '讯飞ISV声纹组ID'")
    private String groupId;

    /** 声纹注册时间 */
    @Column(nullable = false, columnDefinition = "DATETIME NOT NULL COMMENT '声纹注册时间'")
    private LocalDateTime registeredAt;

    /**
     * 声纹过期时间。
     * 讯飞声纹默认24h有效，过期后需重新注册。
     * 定时任务提前2h检查并推送续期提醒。
     */
    @Column(nullable = false, columnDefinition = "DATETIME NOT NULL COMMENT '声纹过期时间（默认24h有效）'")
    private LocalDateTime expiresAt;
}
```

### 5.6 用户映射实体（新增）

```java
/**
 * OA系统用户与飞书ID映射实体。
 *
 * 职责：
 * - 建立 OA 系统（system_users.id）与飞书三种ID的双向映射
 * - 支持通过 userId 查飞书信息，或通过飞书ID反查OA用户
 * - 确保数据一致性和去重（userId 唯一）
 */
@Data
@Table(name = "int_user_mapping", comment = "OA系统用户ID与飞书ID映射表")
public class UserMapping {

    /**
     * OA系统用户ID（关联 system_users 表），唯一约束。
     * 一个OA用户对应一条飞书映射记录。
     */
    @TableId
    @Column(nullable = false, unique = true, columnDefinition = "INT NOT NULL UNIQUE COMMENT 'OA系统用户ID，关联system_users表'")
    private Integer userId;

    /** 用户姓名 */
    @Column(length = 100, nullable = false, columnDefinition = "VARCHAR(100) NOT NULL COMMENT '用户姓名'")
    private String userName;

    /** 飞书用户企业内唯一标识（user_id） */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '飞书用户企业内唯一标识(user_id)'")
    private String feishuUserId;

    /** 飞书用户应用间唯一标识（union_id） */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '飞书用户应用间唯一标识(union_id)'")
    private String feishuUnionId;

    /** 飞书用户应用内唯一标识（open_id） */
    @Column(length = 100, columnDefinition = "VARCHAR(100) COMMENT '飞书用户应用内唯一标识(open_id)'")
    private String feishuOpenId;
}
```

### 5.7 参会确认状态枚举

```java
/**
 * 参会确认状态枚举，记录参会人对会议邀约的响应状态。
 */
@Getter
@AllArgsConstructor
public enum ConfirmStatus {
    /** 待确认 - 卡片已发，等待用户点击 */
    PENDING("待确认"),
    /** 确认参加 - 用户点击"参加" */
    CONFIRMED("确认参加"),
    /** 请假不参加 - 用户点击"请假" */
    DECLINED("请假不参加"),
    /** 超时默认参加 - 24h内未响应，系统自动确认 */
    TIMEOUT_CONFIRMED("超时默认参加");

    /** 状态中文描述 */
    private final String description;
}
```

### 5.8 待办状态枚举

```java
/**
 * 待办状态枚举，驱动会后待办跟踪生命周期。
 */
@Getter
@AllArgsConstructor
public enum TodoStatus {
    /** 待开始 - 默认初始状态 */
    PENDING("待开始"),
    /** 进行中 - 责任人确认开始 */
    IN_PROGRESS("进行中"),
    /** 已完成 - 责任人点击"标记完成" */
    COMPLETED("已完成"),
    /** 已延期 - 超过截止时间未完成，系统自动标记 */
    DELAYED("已延期"),
    /** 已取消 - 发起人取消该待办 */
    CANCELLED("已取消");

    /** 状态中文描述 */
    private final String description;
}
```

### 5.9 优先级枚举

```java
/**
 * 优先级枚举，用于待办事项分级管理。
 */
@Getter
@AllArgsConstructor
public enum Priority {
    /** 高优先级 - 必须在截止前完成，延期自动升级通知发起人 */
    HIGH("高"),
    /** 中优先级 - 默认优先级 */
    MEDIUM("中"),
    /** 低优先级 - 提醒频率降低 */
    LOW("低");

    /** 优先级中文描述 */
    private final String description;
}
```

### 5.10 会议状态枚举

```java
/**
 * 会议状态枚举，驱动会议全生命周期。
 * 状态变迁由 MeetingStateMachine 统一管理，确保合法性。
 */
@Getter
@AllArgsConstructor
public enum MeetingStatus {

    // ==================== ①会前阶段 ====================

    /** 议题收集中 - 发起人正在添加/整理议题 */
    ISSUE_COLLECTING("议题收集中"),
    /** 参会人确认中 - 已推送确认卡片，等待参会人响应 */
    ATTENDEE_CONFIRMING("参会人确认中"),
    /** 议程已发送 - 议程已推送给所有参会人 */
    AGENDA_SENT("议程已发送"),
    /** 已邀约 - 参会确认完成，正式邀约已发送 */
    INVITED("已邀约"),

    // ==================== ②会中阶段 ====================

    /** 已开始 - 会议已正式开始 */
    STARTED("已开始"),
    /** 上次进度通报中 - 正在通报上次会议待办完成情况 */
    REVIEWING("上次进度通报中"),
    /** 录音中 - 正在录制会议语音 */
    RECORDING("录音中"),
    /** 录音暂停 - 茶歇/休息期间暂停录音 */
    PAUSED("录音暂停"),

    // ==================== ③会后阶段 ====================

    /** 纪要处理中 - 离线校正+发言人识别+大模型生成进行中 */
    PROCESSING("纪要处理中"),
    /** 已完成 - 纪要已生成、飞书文档已创建 */
    COMPLETED("已完成"),
    /** 待办跟踪中 - 待办已同步，正在跟踪执行进度 */
    TODO_TRACKING("待办跟踪中"),
    /** 全部完成 - 所有待办均已完成或取消 */
    ALL_DONE("全部完成"),
    /** 已归档 - 会议和待办均已关闭，仅可查看 */
    ARCHIVED("已归档"),

    // ==================== 异常状态 ====================

    /** 异常终止 - 超时或系统错误导致非正常结束 */
    ABORTED("异常终止"),
    /** 已取消 - 发起人主动取消会议 */
    CANCELLED("已取消");

    /** 状态中文描述 */
    private final String description;
}
```

---

## 6. 关键API设计

### 6.1 会前筹备API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 创建会议 | POST | `/api/v1/meetings` | `{title, agenda, company, department, groupName, scheduledTime, creatorId}` | 创建会议草稿，初始状态 ISSUE_COLLECTING；返回 meetingId |
| 收集议题 | POST | `/api/v1/meetings/{id}/agenda` | `{topic, duration, submitter}` | 添加单条议题，追加到agenda JSON数组末尾；返回更新后的议题列表 |
| 确定参会人 | PUT | `/api/v1/meetings/{id}/participants` | `[{userId, name, required}]` | 批量设置参会人（覆盖式更新）；required=true 表示必须参会，拒绝后需调整 |
| 发送议程 | POST | `/api/v1/meetings/{id}/agenda/send` | 无请求体 | 向所有参会人推送议程卡片；状态变更为 AGENDA_SENT |
| 预约会议室 | POST | `/api/v1/meetings/{id}/room` | `{roomId, startTime, endTime}` | 对接飞书日历API预约会议室；成功返回会议室详情 |
| 推送资料 | POST | `/api/v1/meetings/{id}/materials` | `{files: [{url, name}], lastMeetingId}` | 推送上次纪要+关联文件给参会人；自动附上次待办统计 |
| 参会确认 | PUT | `/api/v1/meetings/{id}/participants/{pid}/confirm` | `{action: "confirm"/"decline"}` | 参会人一键确认参加/请假；DECLINED时触发调整提醒给发起人 |
| 发送邀约 | POST | `/api/v1/meetings/{id}/invitation` | 无请求体 | 全部确认后发送正式邀约（飞书日程+多功能卡片）；生成录音链接；状态→INVITED |

### 6.2 会中录音API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 获取录音链接 | GET | `/api/v1/meetings/{id}/recording-url` | — | 生成一次性录音页面URL（含JWT token），返回 `{url, expiresIn}` |
| WebSocket音频推流 | WS | `/ws/audio/{meetingId}?token={jwt}` | 二进制PCM帧（1280字节/40ms） | 浏览器↔后端的实时音频流，后端桥接至ASR引擎；**断线后自动重连（最大3次，间隔1s/3s/5s），前端AudioWorklet本地缓存断线期间的音频帧，重连后补传** |
| 开始录音 | POST | `/api/v1/meetings/{id}/recording/start` | 无请求体 | 标记录音开始，记录 actualStartTime；状态→RECORDING |
| 暂停录音 | POST | `/api/v1/meetings/{id}/recording/pause` | 无请求体 | 暂停推流但保持WebSocket连接；状态→PAUSED |
| 继续录音 | POST | `/api/v1/meetings/{id}/recording/resume` | 无请求体 | 恢复音频推流；状态→RECORDING |
| 结束录音 | POST | `/api/v1/meetings/{id}/recording/stop` | 无请求体 | 关闭WebSocket+ASR连接→触发异步[离线校正→发言人识别→纪要生成]链；状态→PROCESSING |
| 查询状态 | GET | `/api/v1/meetings/{id}/status` | — | 返回当前状态+时长+ASR费用预估 `{status, duration, estimatedCost}` |

### 6.3 会后待办跟踪API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 查看待办列表 | GET | `/api/v1/meetings/{id}/todos` | `?status=&assigneeId=` | 获取本次会议待办列表，支持按状态/责任人筛选 |
| 手动分配责任人 | PUT | `/api/v1/todos/{tid}/assign` | `{assigneeId, assigneeName}` | 发起人手动为未分配或"待确认"待办指定责任人 |
| 更新待办状态 | PUT | `/api/v1/todos/{tid}/status` | `{status, note}` | 责任人更新状态（IN_PROGRESS→COMPLETED）；自动更新int_meeting_participant.completedCount |
| 添加进度说明 | POST | `/api/v1/todos/{tid}/note` | `{note}` | 添加进度备注/卡点说明 |
| 待办看板 | GET | `/api/v1/meetings/{id}/todo-board` | — | 返回 `{total, byStatus: {PENDING:x,...}, byPerson: [{name, total, completed}], delayed: [...]}` |
| 上次会议进度 | GET | `/api/v1/meetings/{id}/previous-progress` | — | 通过 previousMeetingId 查询上次会议待办完成统计 |
| 手动提醒 | POST | `/api/v1/todos/{tid}/remind` | 无请求体 | 发起人手动触发提醒（不重置提醒计数，增加一次提醒记录） |
| 关联下次会议 | PUT | `/api/v1/todos/{tid}/next-meeting` | `{nextMeetingId}` | 将未完成待办挂到下次会议用于通报 |

### 6.4 声纹管理API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 注册声纹 | POST | `/api/v1/voiceprint/register` | `{userId, audioBase64}` | 提交用户朗读音频，调用讯飞ISV API注册；返回 featureId |
| 查询声纹状态 | GET | `/api/v1/voiceprint/status/{userId}` | — | 返回 `{registered, expiresAt, remaining}`，判断声纹是否就绪 |
| 批量预加载 | POST | `/api/v1/meetings/{id}/voiceprint/preload` | 无请求体 | 会前检查所有参会人声纹状态，通过 int_user_mapping 查找飞书ID推送过期提醒 |

### 6.5 用户映射API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 查询飞书ID | GET | `/api/v1/user-mapping/{userId}` | — | 通过OA userId查询飞书 user_id/union_id/open_id |
| 批量查询 | POST | `/api/v1/user-mapping/batch` | `{userIds: [1,2,3]}` | 批量查询OA用户对应的飞书ID |
| 更新映射 | PUT | `/api/v1/user-mapping/{userId}` | `{feishuUserId, feishuUnionId, feishuOpenId}` | 更新/补全用户飞书ID映射信息 |

### 6.6 飞书卡片回调API

| 接口 | 方法 | 路径 | 请求体/参数 | 说明 |
|------|------|------|------------|------|
| 飞书事件推送 | POST | `/api/v1/feishu/callback` | 飞书事件体 | 处理卡片按钮回调（参会确认/待办完成/延期等），由飞书开放平台事件订阅触发 |

---

## 7. 消息队列设计（Kafka）

### 7.1 异步任务分解

```
会议结束事件（RecordingController.stop 触发）
    │
    ├──→ Kafka Topic: meeting.events
    │    │  Partition Key: meetingId（保证同一会议事件有序）
    │    │
    │    └── Consumer Group: minute-generate
    │        消费链：ASR离线校正 → 发言人识别(ISV) → 大模型生成纪要 → 创建飞书文档
    │        回调：更新int_meeting状态为COMPLETED → 通知用户纪要已生成
    │             ↓
    │        纪要写入飞书文档完成后
    │             ↓
    │        发送消息到 Kafka Topic: todo.extract
    │             ↓
    ├──→ Kafka Topic: todo.extract
    │    │  Partition Key: meetingId
    │    │
    │    └── Consumer Group: todo-extract
    │        消费链：从纪要文本提取待办 → 按人分配 → 同步飞书任务API
    │        回调：更新int_meeting状态为TODO_TRACKING
    │
    └──→ Redis ZSet: todo:reminders
         │  key = "todo:reminders"，score = triggerAt（Unix毫秒）
         │  生产者按 deadline-24h / deadline-2h / daily-remind 计算目标时间
         │  value = {todoId, meetingId, type, remindCount}
         │
         └──→ 定时任务（每分钟扫描）
              │  XXL-Job: scanRemindZSet (Cron: 0 * * * * ?)
              │  ZRANGEBYSCORE todo:reminders 0 now → 取到期消息
              │  发送飞书卡片提醒 → ZREM 移除已处理项
              │  处理失败 → 记录错误日志，下次重试（不进入DLQ）
```

> **设计说明**：纪要生成（minute-generate）与待办提取（todo-extract）为**串行依赖**关系，必须等纪要写入飞书文档完成后才提取待办，避免提取到不完整的纪要。改用独立 `todo.extract` Topic 而非同一 Topic 的两个消费者组，消除消费顺序不确定性。延时提醒改用 Redis ZSet 实现，比 Kafka 延时消息更轻量、更精确。

### 7.2 Kafka Topic 设计

| Topic | 分区数 | 消费者组 | 说明 |
|-------|--------|----------|------|
| `meeting.events` | 3 | `minute-generate` | 会议事件（开始/结束/状态变更），触发纪要生成链 |
| `todo.extract` | 3 | `todo-extract` | 待办提取（纪要写入飞书文档后发送） |
| （Redis ZSet）`todo:reminders` | — | — | 待办延时提醒，score=triggerAt，XXL-Job每分钟扫描到期项 |

### 7.3 消息体定义（含字段注释）

```java
/**
 * 纪要生成消息。
 * 在会议结束（录音停止）时发送，触发异步的离线校正+发言人识别+纪要生成链。
 */
@Data
public class MinuteGenerateMessage {
    /** 会议ID，消费者据此查询会议信息和转录分段 */
    private String meetingId;

    /** 音频文件本地缓存路径，离线校正需要从此路径读取完整音频 */
    private String audioPath;

    /** 参会人声纹特征ID列表，用于ISV说话人识别时限定搜索范围 */
    private List<String> featureIds;

    /** 大模型名称（如 deepseek-v4-pro），由配置中心获取 */
    private String modelName;

    /** 消息发送时间戳，消费者据此判断是否超时（超过30min告警） */
    private Long sentAt;

    /** Kafka分区键（meetingId），保证同一会议事件有序 */
    private String partitionKey;
}

/**
 * 待办提醒消息（写入 Redis ZSet）。
 * score = triggerAt.toInstant(ZoneOffset.of("+8")).toEpochMilli()，value 为 JSON 序列化本对象。
 * 在待办同步完成后写入，按截止时间或每日定时计算 triggerAt。
 * XXL-Job 每分钟 ZRANGEBYSCORE 扫描到期项，发送飞书卡片提醒后 ZREM 移除。
 */
@Data
public class TodoRemindMessage {
    /** 待办ID */
    private String todoId;

    /** 所属会议ID */
    private String meetingId;

    /**
     * 提醒类型。
     * DEADLINE_24H：截止前24小时提醒
     * DEADLINE_2H：截止前2小时紧急提醒
     * DAILY_REMIND：每天定时提醒（如8:30）
     */
    private RemindType type;

    /** 计划触发时间（Unix毫秒，用作Redis ZSet score） */
    private Long triggerAtMillis;

    /** 已提醒次数（据此判断是否超过阈值需升级通知） */
    private Integer remindCount;
}
```

---

## 8. 定时任务设计

| 任务名称 | 频率 | 执行器 | 说明 |
|---------|------|--------|------|
| **checkTodoNearDeadline** | 每小时（Cron: `0 0 * * * ?`） | XXL-Job | 扫描截止时间<24h的待办（status=PENDING/IN_PROGRESS），写入Redis ZSet提醒消息 |
| **dailyTodoRemind** | 每天 8:30（Cron: `0 30 8 * * ?`） | XXL-Job | 扫描所有未完成待办（status=PENDING/IN_PROGRESS），批量写入每日提醒到Redis ZSet |
| **autoMarkDelayed** | 每天 9:00（Cron: `0 0 9 * * ?`） | XXL-Job | 扫描超过截止时间仍未完成的待办，自动标记为 DELAYED；推送通知给发起人 |
| **checkVoiceprintExpiry** | 每天 8:00（Cron: `0 0 8 * * ?`） | XXL-Job | 扫描声纹即将过期（距 expiresAt < 2h）的用户，通过 int_user_mapping 查飞书ID推送续期提醒 |
| **scanRemindZSet** | 每分钟（Cron: `0 * * * * ?`） | XXL-Job | 扫描Redis ZSet中已到期的待办提醒（ZRANGEBYSCORE 0 now），发送飞书卡片提醒后ZREM移除 |
| **checkRecordingTimeout** | 每 10 分钟（Cron: `0 */10 * * * ?`） | XXL-Job | 扫描状态为 RECORDING/PAUSED 的会议：录制超4h→自动停止；静音超30min→自动暂停+提醒 |
| **cleanAudioCache** | 每天 3:00（Cron: `0 0 3 * * ?`） | XXL-Job | 清理超过168h（7天）的音频缓存文件，删除对应目录，记录清理日志 |
| **reportPreviousProgress** | 会议状态变为 STARTED 时触发 | 事件驱动 | 非定时任务——由状态机在 STARTED 事件中，通过 previousMeetingId 查询并发送上次待办统计卡片 |

---

## 9. 飞书卡片交互设计

### 9.1 会议邀约卡片

```json
{
  "config": { "wide_screen_mode": true },
  "header": {
    "title": { "tag": "plain_text", "content": "📋 Q2产品评审会" },
    "template": "blue"
  },
  "elements": [
    {
      "tag": "div",
      "text": {
        "tag": "lark_md",
        "content": "🕐 **时间**：2026-05-09 14:00-15:00\n📍 **地点**：3楼会议室A / [线上链接]\n👥 **参会人**：张三、李四、王五\n\n📝 **议题**：\n1. Q2产品规划评审（15分钟）\n2. 技术方案讨论（20分钟）\n3. 资源分配确认（10分钟）"
      }
    },
    {
      "tag": "hr"
    },
    {
      "tag": "action",
      "actions": [
        {
          "tag": "button",
          "text": { "tag": "lark_md", "content": "✅ 参加" },
          "type": "primary",
          "value": { "meetingId": "mtg_uuid_xxx", "participantId": "pid_xxx", "action": "confirm" }
        },
        {
          "tag": "button",
          "text": { "tag": "lark_md", "content": "❌ 请假" },
          "type": "default",
          "value": { "meetingId": "mtg_uuid_xxx", "participantId": "pid_xxx", "action": "decline" }
        },
        {
          "tag": "button",
          "text": { "tag": "lark_md", "content": "🎤 打开录音" },
          "type": "primary",
          "url": "https://{domain}/recorder/{meetingId}?token={jwt}"
        }
      ]
    },
    {
      "tag": "note",
      "elements": [
        { "tag": "lark_md", "content": "💡 上次会议共3项待办：✅2项已完成 🔄1项进行中\n请在24小时内确认参会，超时将默认参加" }
      ]
    }
  ]
}
```

### 9.2 待办提醒卡片

```json
{
  "config": { "wide_screen_mode": true },
  "header": {
    "title": { "tag": "plain_text", "content": "🔔 待办提醒" },
    "template": "orange"
  },
  "elements": [
    {
      "tag": "div",
      "text": {
        "tag": "lark_md",
        "content": "📋 **来源会议**：Q2产品评审会（5月9日）\n\n📌 **待办内容**：完成Q2前端重构方案\n⏰ **截止时间**：2026-05-16 18:00（还剩**24小时**）\n🔴 **优先级**：高\n\n⚠️ 若无法按时完成，请及时申请延期并说明原因"
      }
    },
    {
      "tag": "hr"
    },
    {
      "tag": "action",
      "actions": [
        {
          "tag": "button",
          "text": { "tag": "lark_md", "content": "✅ 标记完成" },
          "type": "primary",
          "value": { "todoId": "todo_uuid_xxx", "action": "complete" }
        },
        {
          "tag": "button",
          "text": { "tag": "lark_md", "content": "⏸️ 申请延期" },
          "type": "default",
          "value": { "todoId": "todo_uuid_xxx", "action": "delay" }
        }
      ]
    }
  ]
}
```

### 9.3 上次进度通报卡片

```json
{
  "config": { "wide_screen_mode": true },
  "header": {
    "title": { "tag": "plain_text", "content": "📊 上次会议待办进度" },
    "template": "wathet"
  },
  "elements": [
    {
      "tag": "div",
      "text": {
        "tag": "lark_md",
        "content": "📋 **上次会议**：Q2产品评审会（5月9日）\n\n**待办总览**：共5项\n✅ 已完成：3项\n🔄 进行中：1项\n❌ 已延期：1项\n\n---\n\n**延期项详情**：\n🔴 Q2前端重构方案（责任人：张三，原截止5月16日）\n   延期原因：依赖后端接口未就绪\n\n---\n\n**进行中**：\n🔄 技术方案评审（责任人：李四，截止5月20日）"
      }
    },
    {
      "tag": "note",
      "elements": [
        { "tag": "lark_md", "content": "本次会议将基于以上进度继续讨论，延期项请责任人准备说明" }
      ]
    }
  ]
}
```

---

## 10. 非功能需求

### 10.1 性能要求

| 指标 | 目标值 | 备注 |
|------|--------|------|
| 实时转写延迟 | ≤2秒（语音→文字显示） | WebSocket全双工，Netty非阻塞I/O |
| 纪要生成时间 | 会议结束后≤5分钟 | 异步Kafka消息处理，不含大模型排队时间 |
| 并发会议支持 | ≥10场独立会议 | 每场会议独立WebSocket连接+线程池隔离 |
| 音频推流稳定性 | 丢帧率<0.1% | 前端本地缓存+WebSocket断线重连补偿；PCM实时推流，录音结束后异步压缩为Ogg/Opus归档 |
| API响应时间 | P99 < 500ms | MySQL索引+Redis缓存热点数据 |

### 10.1.1 音频存储规范

| 格式 | 用途 | 保留期 | 说明 |
|------|------|--------|------|
| **PCM**（16kHz/16bit/单声道） | 实时ASR转写，断线重连补传 | 7天 | 录音过程中实时推流，转写完成后保留于 `/data/audio/{date}/{meetingId}.pcm`，定时任务清理 |
| **Ogg/Opus** | 归档存储 | 6个月 | 录音结束后异步压缩，用于事后回溯、离线校正复核、审计取证 |

### 10.2 准确率要求

| 指标 | 目标值 | 依赖 |
|------|--------|------|
| 语音转文字准确率 | ≥95%（普通话） | 讯飞ASR引擎质量 |
| 说话人分离准确率 | ≥90%（3-5人，声纹注册） | 讯飞ISV声纹识别 |
| 待办事项提取完整率 | ≥85% | 大模型Prompt质量+纪要结构化程度 |
| 人名匹配准确率 | ≥90%（声纹注册模式） | 声纹注册音频质量+ISV搜索阈值 |

### 10.3 安全要求

| 项目 | 要求 | 实现方式 |
|------|------|----------|
| 音频传输 | WebSocket + TLS 1.3加密 | Nginx反向代理 + SSL证书 |
| API鉴权 | Spring Security + JWT + 飞书token双重验证 | SecurityConfig + OncePerRequestFilter |
| API密钥 | 禁止硬编码，环境变量+Nacos配置中心管理 | application.yml中仅写占位符 `${...}` |
| 用户数据 | 音频文件保留168h（7天）后自动删除；纪要数据保留至归档后6个月 | 定时任务清理 + 归档策略 |
| 文档权限 | 默认仅会议参会人可访问（飞书文档API设置协作者） | FeishuDocService.setPermission() |
| 审计日志 | AOP切面记录所有关键操作（创建/状态变更/删除/权限变更） | AuditLogAspect，记录{操作人,时间,类型,目标ID,结果} |

### 10.4 可用性要求

| 项目 | 要求 | 实现方式 |
|------|------|----------|
| 服务可用性 | 99.9%（月停机<43分钟） | 单实例部署+健康检查+自动重启 |
| 优雅关闭 | 录音中的会议自动保存并通知用户 | Spring @PreDestroy + ShutdownHook |
| 异常恢复 | 断线重连、Kafka消费重试（3次→DLQ）、定时任务补偿 | 各层独立重试策略 |
| 监控告警 | Prometheus + Grafana + 飞书告警 | 关键指标（QPS/延迟/错误率/Consumer Lag） |

---

## 11. 成本估算（月度）

### 11.1 API调用成本（按中小团队20场会议估算）

| 项目 | 单价 | 月用量 | 月成本 |
|------|------|--------|--------|
| 科大讯飞实时ASR | 0.33元/分钟 | 20场×45min = 900min | ≈297元 |
| 科大讯飞ISV声纹 | 0.05元/次 | 20场×5人×1次 = 100次 | ≈5元 |
| 大模型纪要生成 | 0.01元/千token | 20场×10k token | ≈2元 |
| 飞书API | 免费 | — | 0元 |
| **合计（讯飞主方案）** | | | **≈304元/月** |

### 11.2 服务器成本（Java服务需稍高配置）

| 配置 | 月成本 | 适用场景 |
|------|--------|----------|
| 2核4G（MySQL+Redis共用） | ≈120-200元 | ≤5并发，个人/小团队PoC |
| 4核8G + 独立MySQL 2核4G | ≈400-600元 | ≤20并发，中型团队生产环境 |
| 8核16G + MySQL主从 + Redis集群 | ≈1000-1500元 | ≤50并发，大型团队高可用 |

---

## 12. 交付计划

### 12.1 分期交付

| 阶段 | 时间 | 范围 | 交付物 |
|------|------|------|--------|
| **P0-MVP** | 第1-5天 | 会中核心（录音+ASR+纪要+文档） | Spring Boot骨架+WebSocket音频桥接+讯飞ASR对接+纪要生成+飞书文档创建 |
| **P1-闭环** | 第6-10天 | 会前筹备+会后跟踪闭环 | 议题收集+参会人管理+参会确认+邀约；待办拆解+飞书任务同步+进度提醒+上次会议通报 |
| **P2-声纹** | 第11-13天 | 声纹注册+说话人识别+用户映射 | 声纹注册页面+讯飞ISV对接+int_user_mapping映射+纪要人名标注 |
| **P3-增强** | 第14-18天 | 离线模式+看板+体验优化 | 离线录音上传+待办看板+前端体验优化 |

### 12.2 P0-MVP 详细计划

| 天数 | 工作内容 | 产出 |
|------|----------|------|
| Day 1 | Spring Boot 3 项目初始化（多模块Maven）+ 数据库DDL + 飞书Webhook对接 + 指令解析 | 项目骨架可运行，/api/v1/health 返回200 |
| Day 2 | Web录音页面开发（getUserMedia → AudioContext(16kHz) → AudioWorkletNode → PCM → WebSocket推流） | 录音页面可独立打开，音频流可发送至后端 |
| Day 3 | 音频桥接模块（Spring WebFlux WebSocket ↔ 讯飞实时ASR WebSocket）+ 音频分片缓存 | 实时转写可推回前端页面显示 |
| Day 4 | 大模型纪要生成（Kafka异步）+ 飞书文档创建（Block结构化写入） | 结束会议→生成纪要→创建文档可跑通 |
| Day 5 | 全链路联调 + 集成测试 + Bug修复 + Docker Compose打包 | 一键 `docker-compose up` 启动可用 |

---

## 13. 风险与预案

| 风险 | 等级 | 影响 | 预案 |
|------|------|------|------|
| 参会人不确认导致流程阻塞 | 🟡 中 | 会议卡在"参会确认中"无法推进 | ①配置确认超时24h，超时自动确认 ②允许发起人"强制推进"跳过确认 |
| 上次会议无边可通报（首次会议/已归档） | 🟢 低 | 会议开场卡片空白 | previousMeetingId 为 NULL → 跳过"上次进度通报"卡片，直接进入录音 |
| 待办长时间未完成/无限延期 | 🟡 中 | 闭环断裂，下次会议反复通报同一待办 | ①maxDelayCount限制延期次数 ②自动升级通知发起人 ③可手动标记CANCELLED |
| 飞书机器人无法推送外链（录音页面） | 🟡 中 | 用户无法打开录音页面 | Python版已解决：ngrok/cpolar内网穿透 + 飞书企业域名白名单，借鉴即可 |
| 用户忘记结束会议 | 🔴 高 | 录音持续消耗ASR配额 | ①4h自动停止+通知 ②30min无语音自动暂停+提醒 ③每小时推送状态卡片 |
| 科大讯飞API并发超限 | 🟡 中 | 实时转写失败，新会议无法开始录音 | ①AsrOrchestrator自动切换腾讯云备选 ②购买更高并发套餐 ③会议排队机制 |
| Kafka消息积压 | 🟡 中 | 纪要生成/待办同步延迟 | ①消费者扩容（提高concurrency） ②纪要生成优先分区 ③Consumer Lag超100条飞书告警 |
| Spring Boot内存泄漏 | 🟢 低 | 1核2G内存不足，频繁GC影响延迟 | ①G1GC + MaxRAMPercentage=75 ②WebSocket空闲超时自动关闭 ③必要时升配2核4G |
| OA↔飞书ID映射缺失 | 🟡 中 | 无法通过user_id推送消息/分配任务 | ①提供管理员手动维护映射的接口 ②飞书事件触发时自动补全映射 ③定时同步飞书通讯录全量拉取 |
| WebSocket断线导致音频丢失 | 🔴 高 | 断线期间音频帧未缓存，导致转写内容缺失 | ①前端AudioWorklet本地缓存断线期间的音频帧 ②断线后自动重连（最大3次，间隔1s/3s/5s） ③重连后补传缓存帧 ④PCM实时推流+录音结束后异步压缩Ogg/Opus归档作为兜底 |

---

## 14. 与v1.1版的差异对照

| 维度 | v1.1-java | v2.0-java | v2.1-java（本版） |
|------|-----------|-----------|-------------------|
| **用户标识** | open_id（应用内唯一） | user_id（企业内唯一），辅以 int_user_mapping 表建立OA↔飞书双射 | 不变 |
| **表名前缀** | meeting / meeting_participant / meeting_todo | int_meeting / int_meeting_participant / int_meeting_todo | 不变 |
| **meeting表结构** | 无company/department/group字段 | 新增company(集团)、department(部门)、groupName(会议组) | 不变 |
| **voiceprint表** | 以open_id为主键关联用户 | 以userId(int)关联OA系统用户，feishuUserId通过mapping映射获取 | 新增feishu_user_id索引 |
| **新增表** | — | int_user_mapping（OA用户ID↔飞书三种ID映射） | 不变 |
| **消息队列** | RabbitMQ（延时队列+死信） | Kafka（高吞吐+分区有序+DLQ Topic） | todo.extract独立Topic；todo.reminders→Redis ZSet |
| **声纹注册API** | 参数为openId | 参数为userId(int) | 不变 |
| **参与人/待办责任人** | openId | userId（飞书user_id） | 不变 |
| **配置更新** | 本地开发环境占位符 | 真实数据库连接 + 每日8:30定时提醒 + 延期次数可配 | 不变 |
| **枚举定义** | 仅MeetingStatus | 补全TODO_TRACKING状态 | 新增ConfirmStatus/TodoStatus/Priority |
| **风险项** | 7项 | 新增"OA↔飞书ID映射缺失"风险 | 新增"WebSocket断线导致音频丢失" |

---

## 附录A：数据库表结构（DDL-含完整字段注释）

```sql
-- ============================================================
-- 智能会议系统 - 数据库初始化脚本
-- 数据库：MySQL 8.0+ (InnoDB)
-- 字符集：utf8mb4
-- 创建日期：2026-05-08
-- 版本：v2.1（15项查缺补漏修复）
-- ============================================================

CREATE DATABASE IF NOT EXISTS smart_meeting
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE smart_meeting;

-- ============================================================
-- 1. 会议主表
-- 记录每次会议的全生命周期信息，是系统的核心实体
-- 状态字段驱动业务流程（会前→会中→会后→归档）
-- previous_meeting_id 实现会议间闭环关联
-- 新增 company/department/group 字段支持多集团多部门
-- ============================================================
CREATE TABLE int_meeting (
    -- 唯一标识
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '会议唯一标识，UUID v4生成，36字符',

    -- 基本信息
    title           VARCHAR(200) NOT NULL COMMENT '会议主题/标题，如"Q2产品评审会"，飞书卡片展示',
    agenda          JSON         NULL     COMMENT '议题列表（JSON数组），结构：[{"no":1,"topic":"标题","duration":15,"submitter":"张三"}]',
    company         VARCHAR(200) NOT NULL COMMENT '所属集团，如"集团"、"艾科森"等',
    department      VARCHAR(200) NULL     COMMENT '集团部门，没有可不填',
    group_name      VARCHAR(200) NOT NULL COMMENT '会议组，如"经管会"、"技术委员会"、"周例会"',

    -- 状态驱动（关联 MeetingStatus 枚举）
    status          VARCHAR(30)  NOT NULL COMMENT '会议当前状态：ISSUE_COLLECTING/ATTENDEE_CONFIRMING/AGENDA_SENT/INVITED/STARTED/REVIEWING/RECORDING/PAUSED/PROCESSING/COMPLETED/TODO_TRACKING/ALL_DONE/ARCHIVED/ABORTED/CANCELLED',

    -- 人员
    creator_id      VARCHAR(64)  NOT NULL COMMENT '会议发起人飞书user_id，拥有会议管理权限',

    -- 场地
    room_id         VARCHAR(64)  NULL     COMMENT '会议室ID（线下实体会议室）或飞书视频会议ID（线上），NULL表示未预约',

    -- 闭环关联（流程图核心字段）
    previous_meeting_id VARCHAR(36) NULL  COMMENT '上次会议ID，用于下次会议开场通报上次待办进度。首次会议（无上次会议）时为NULL',

    -- 时间
    scheduled_time  DATETIME     NULL     COMMENT '会议预定时间，发起人创建时设定',
    actual_start_time DATETIME   NULL     COMMENT '会议实际开始时间，用户点击"开始录音"时记录',
    actual_end_time DATETIME     NULL     COMMENT '会议实际结束时间，用户发送"结束会议"或超时自动停止时记录',
    duration_seconds INT         NULL     COMMENT '录音总时长（秒），由end-start计算或音频元数据提取',

    -- 音频存储
    audio_path      VARCHAR(500) NULL     COMMENT '音频文件本地缓存路径，格式：/data/audio/{yyyyMMdd}/{meetingId}.pcm',

    -- 飞书文档
    doc_url         VARCHAR(500) NULL     COMMENT '飞书纪要文档访问URL，纪要生成后通过卡片返回给用户',
    doc_token       VARCHAR(100) NULL     COMMENT '飞书文档唯一token，用于调用飞书文档API进行内容写入/权限设置',

    -- 录音页面
    recording_url   VARCHAR(500) NULL     COMMENT 'Web录音页面完整URL，格式：https://{domain}/recorder/{meetingId}?token={jwt}',
    recording_token VARCHAR(100) NULL     COMMENT '录音页面一次性鉴权JWT token，有效期4h（与最大录音时长一致）',

    -- 审计字段
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间，每次UPDATE自动刷新',

    -- 索引
    INDEX idx_meeting_status (status)          COMMENT '按状态查询：查看进行中的会议、归档的会议等',
    INDEX idx_meeting_creator (creator_id)     COMMENT '按发起人查询：我的会议列表',
    INDEX idx_meeting_previous (previous_meeting_id) COMMENT '按上次会议查询：闭环通报时通过此索引快速定位',
    INDEX idx_meeting_company_group (company, group_name) COMMENT '按集团+会议组查询：跨部门会议检索',

    -- 表注释
    COMMENT = '会议主表，记录每次会议的全生命周期信息。状态驱动业务流程，previous_meeting_id实现闭环。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. 参会人表
-- 记录每个会议的参与人员及其参会确认、声纹、待办统计
-- 使用 user_id（飞书企业内唯一标识）
-- ============================================================
CREATE TABLE int_meeting_participant (
    -- 唯一标识
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '参会人记录唯一标识，UUID',

    -- 关联
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '所属会议ID，外键关联 int_meeting.id',

    -- 用户信息
    user_id         VARCHAR(64)  NOT NULL COMMENT '飞书用户user_id，用于消息推送/日历邀请/任务分配',
    name            VARCHAR(100) NOT NULL COMMENT '参会人姓名，用于卡片展示和纪要标注',

    -- 参会确认
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '参会确认状态：PENDING(待确认)/CONFIRMED(确认参加)/DECLINED(请假)/TIMEOUT_CONFIRMED(超时默认参加)',

    -- 声纹关联
    feature_id      VARCHAR(100) NULL     COMMENT '讯飞ISV声纹特征ID，24h有效。NULL表示未注册声纹，会后使用盲分模式',
    voiceprint_ready TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '声纹是否就绪：1=已注册且未过期，0=未注册或已过期',

    -- 待办统计（冗余字段，用于参会人列表快速展示完成率）
    todo_count      INT          NOT NULL DEFAULT 0 COMMENT '该参会人在本次会议中分配的待办总数',
    completed_count INT          NOT NULL DEFAULT 0 COMMENT '该参会人在本次会议中已完成的待办数',

    -- 约束与索引
    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE COMMENT '级联删除：会议删除时同步删除参会人记录',
    UNIQUE KEY uk_meeting_user (meeting_id, user_id) COMMENT '同一会议同一用户只能出现一次',
    INDEX idx_participant_meeting (meeting_id) COMMENT '按会议查询参会人列表',
    INDEX idx_participant_userid (user_id)     COMMENT '按用户查询其参与的所有会议',

    COMMENT = '会议参会人表，记录参会确认状态、声纹关联和待办完成统计。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. 转录分段表
-- 记录会议语音转文字的每一句话（segment）
-- 按会议+时间排序，形成完整的对话记录
-- 声纹匹配前speaker_id为编号，匹配后更新为真实姓名
-- ============================================================
CREATE TABLE int_transcript_segment (
    -- 唯一标识
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '分段唯一标识，UUID',

    -- 关联
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '所属会议ID，外键关联 int_meeting.id',

    -- 说话人
    speaker_id      VARCHAR(100) NULL     COMMENT '说话人标识：声纹匹配前为speaker_0/speaker_1编号，匹配后更新为真实姓名',
    speaker_name    VARCHAR(100) NULL     COMMENT '说话人显示名称，用于飞书文档发言人标注',

    -- 时间
    start_time_ms   INT          NOT NULL COMMENT '语音开始时间偏移（毫秒），用于多路音频时间戳对齐',
    end_time_ms     INT          NOT NULL COMMENT '语音结束时间偏移（毫秒）',

    -- 内容
    text            TEXT         NOT NULL COMMENT '识别/校正后的文字内容',

    -- 质量标记
    is_final        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为最终结果：0=实时ASR中间结果(会覆盖更新)，1=最终结果(不再变化)',
    confidence      DOUBLE       NULL     COMMENT 'ASR识别置信度(0.0-1.0)，<0.7的片段触发离线大模型文本纠错',
    corrected       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已校正：0=原始ASR结果，1=已通过离线校正',

    -- 约束与索引
    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE COMMENT '级联删除',
    INDEX idx_segment_meeting_time (meeting_id, start_time_ms) COMMENT '按会议+时间排序查询完整对话记录',

    COMMENT = '转录分段表，记录每次会议的语音转文字结果（每句话一个分段）。支持实时+离线双模式。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. 待办表（会后跟踪核心）
-- 从会议纪要中拆解出的行动项，驱动会后进度提醒+跟踪+闭环
-- 通过 next_meeting_id 实现"回流至下次会议"
-- 责任人使用 user_id（飞书企业内唯一标识）
-- 状态流转：PENDING→IN_PROGRESS→COMPLETED (正常)
--          PENDING/IN_PROGRESS→DELAYED→重新设截止→IN_PROGRESS
-- ============================================================
CREATE TABLE int_meeting_todo (
    -- 唯一标识
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '待办唯一标识，UUID',

    -- 关联
    meeting_id      VARCHAR(36)  NOT NULL COMMENT '来源会议ID，外键关联 int_meeting.id',

    -- 任务内容
    content         TEXT         NOT NULL COMMENT '待办内容描述，1.大模型从纪要中提取 2.发起人手动添加',

    -- 责任人
    assignee_id     VARCHAR(64)  NOT NULL COMMENT '责任人飞书user_id，用于任务分配和消息提醒推送',
    assignee_name   VARCHAR(100) NULL     COMMENT '责任人姓名，用于卡片展示和看板统计',

    -- 状态与优先级
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '待办状态：PENDING(待开始)/IN_PROGRESS(进行中)/COMPLETED(已完成)/DELAYED(已延期)/CANCELLED(已取消)',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：HIGH(高-延期自动升级)/MEDIUM(中-默认)/LOW(低-降低提醒频率)',

    -- 截止与完成
    deadline        DATETIME     NULL     COMMENT '任务截止时间，用于计算提醒触发点(24h/2h)和延期判断',
    completed_at    DATETIME     NULL     COMMENT '实际完成时间，责任人点击"标记完成"或状态变更为COMPLETED时记录',
    completion_note TEXT         NULL     COMMENT '完成说明，责任人标记完成时可选填写的文字说明',
    block_reason    TEXT         NULL     COMMENT '卡点/延期原因，责任人申请延期时填写，用于下次会议通报中展示',

    -- 提醒跟踪
    last_remind_at  DATETIME     NULL     COMMENT '上次提醒发送时间，用于免打扰判断（距上次提醒<2h不重复提醒）',
    remind_count    INT          NOT NULL DEFAULT 0 COMMENT '累计提醒次数，超过阈值自动升级通知发起人',

    -- 闭环关联（流程图核心字段）
    next_meeting_id VARCHAR(36)  NULL     COMMENT '关联的下次会议ID，待办将在该会议开场时通报完成情况。若在下次会议前已完成则为NULL',
    reported_in_next TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否已在下次会议中通报过：0=未通报，1=已通报（防止重复通报）',

    -- 审计
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '待办创建时间',

    -- 约束与索引
    FOREIGN KEY (meeting_id) REFERENCES int_meeting(id) ON DELETE CASCADE COMMENT '级联删除',
    INDEX idx_todo_meeting (meeting_id)          COMMENT '按会议查询待办列表',
    INDEX idx_todo_assignee (assignee_id)        COMMENT '按责任人查询：我的待办',
    INDEX idx_todo_status_deadline (status, deadline) COMMENT '定时任务扫描：查找临期/延期待办',

    COMMENT = '会议待办表，会后跟踪核心。记录拆解后的行动项及完整的跟踪信息，支撑进度提醒→进度跟踪→下次通报的闭环。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 5. 声纹表
-- 存储用户注册的讯飞ISV声纹特征信息
-- 通过 user_id 关联 OA 系统用户，feishu_user_id 从 int_user_mapping 映射获取
-- 声纹默认24h有效，定时任务提前2h检查并推送续期提醒
-- ============================================================
CREATE TABLE int_voiceprint (
    -- 唯一标识
    id              VARCHAR(36)  NOT NULL PRIMARY KEY COMMENT '记录唯一标识，UUID',

    -- 用户信息
    user_id         INT          NOT NULL UNIQUE COMMENT 'OA系统用户ID（关联system_users表），唯一约束：一个用户只能有一条活跃声纹记录',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名，注册时填写',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书user_id，注册声纹时可不填写，后续从int_user_mapping表中根据user_id映射获取',

    -- 声纹特征
    feature_id      VARCHAR(100) NOT NULL COMMENT '讯飞ISV声纹特征ID，由声纹注册API返回',
    group_id        VARCHAR(100) NULL     COMMENT '讯飞ISV声纹组ID，所有用户注册到同一组，会后在此组内做1:N搜索',

    -- 生命周期
    registered_at   DATETIME     NOT NULL COMMENT '声纹注册时间',
    expires_at      DATETIME     NOT NULL COMMENT '声纹过期时间（讯飞默认24h有效），过期后需重新注册。定时任务提前2h检查并推送续期提醒',

    -- 索引
    INDEX idx_voiceprint_feishu_user (feishu_user_id) COMMENT '按飞书user_id查询声纹信息',

    COMMENT = '声纹表，存储用户注册的讯飞ISV声纹特征信息。24h有效，过期需续期。通过user_id关联OA系统用户。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 6. OA系统用户与飞书ID映射表
-- 建立 OA 系统（system_users.id）与飞书三种ID的双向映射
-- 支持通过 userId 查飞书信息，或通过飞书ID反查OA用户
-- ============================================================
CREATE TABLE int_user_mapping (
    -- 用户信息
    user_id         INT          NOT NULL PRIMARY KEY COMMENT 'OA系统用户ID（关联system_users表），主键',
    user_name       VARCHAR(100) NOT NULL COMMENT '用户姓名',
    feishu_user_id  VARCHAR(100) NULL     COMMENT '飞书用户企业内唯一标识(user_id)',
    feishu_union_id VARCHAR(100) NULL     COMMENT '飞书用户应用间唯一标识(union_id)',
    feishu_open_id  VARCHAR(100) NULL     COMMENT '飞书用户应用内唯一标识(open_id)',

    INDEX idx_feishu_user_id (feishu_user_id) COMMENT '按飞书user_id反查OA用户',
    INDEX idx_feishu_open_id (feishu_open_id) COMMENT '按飞书open_id反查OA用户',

    COMMENT = 'OA系统用户ID与飞书ID映射表，支持双向查询。'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 附录B：配置项清单（含完整注释）

```yaml
# ============================================================
# Smart Meeting - Java版 主配置文件
# 环境：development / production
# 敏感信息通过环境变量注入，禁止硬编码
# 版本：v2.1（Redis ZSet替代Kafka延时提醒，todo.extract独立Topic）
# ============================================================

server:
  port: 8765                                 # 服务端口（与ngrok/nginx转发一致）

spring:
  application:
    name: smart-meeting-server               # 服务名，用于Nacos注册和日志标识

  # ---------- 数据源配置 ----------
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:oabp}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${DB_USERNAME}                 # 数据库用户名（生产环境必须通过环境变量设置）
    password: ${DB_PASSWORD}                 # 数据库密码（生产环境必须通过环境变量设置）
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:                                  # HikariCP连接池
      maximum-pool-size: 20                  # 最大连接数
      minimum-idle: 5                        # 最小空闲连接
      idle-timeout: 300000                   # 空闲超时（毫秒）
      connection-timeout: 10000              # 获取连接超时（毫秒）

  # ---------- Redis配置 ----------
  data:
    redis:
      host: ${REDIS_HOST:localhost}          # Redis主机地址
      port: ${REDIS_PORT:6379}               # Redis端口
      password: ${REDIS_PASSWORD:}           # Redis密码（如无密码留空）
      database: 0                            # 使用0号数据库
      timeout: 5000ms                        # 连接超时
      lettuce:                               # Lettuce客户端
        pool:
          max-active: 16                     # 最大活跃连接
          max-idle: 8                        # 最大空闲连接
          min-idle: 2                        # 最小空闲连接

  # ---------- Kafka配置 ----------
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      retries: 3                             # 发送失败重试次数
      acks: all                              # 等待所有副本确认
    consumer:
      group-id: smart-meeting                # 消费者组ID
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest            # 从最早未消费的消息开始
      enable-auto-commit: false              # 手动提交offset
      max-poll-records: 50                   # 每次拉取最大条数
    listener:
      concurrency: 3                         # 消费者并发数
      ack-mode: manual                       # 手动确认模式（处理完成后commit）

# ============================================================
# 业务配置
# ============================================================
meeting:
  # ---------- 音频参数 ----------
  audio:
    sample-rate: 16000                       # 采样率（Hz），讯飞ASR要求16kHz
    bit-depth: 16                            # 位深度（bit），标准PCM为16bit
    channels: 1                              # 声道数：1=单声道（ASR推荐）
    frame-duration-ms: 40                    # 每帧时长（毫秒），40ms × 16kHz × 2字节 = 1280字节/帧
    max-duration-hours: 4                    # 最大录音时长（小时），超时自动停止并通知
    silence-auto-pause-min: 30               # 连续静音时长（分钟），VAD检测到无声后自动暂停录音
    cache-retention-hours: 168               # 音频缓存保留时长（小时），168=7天，超期后定时任务清理

  # ---------- ASR引擎配置 ----------
  asr:
    primary: xfyun                           # 主ASR引擎：xfyun=科大讯飞
    fallback: tencent                        # 备选ASR引擎：tencent=腾讯云（主引擎不可用时自动切换）
    xfyun:
      app-id: ${XFYUN_APP_ID}                # 讯飞应用ID（控制台获取）
      api-key: ${XFYUN_API_KEY}              # 讯飞API Key
      api-secret: ${XFYUN_API_SECRET}        # 讯飞API Secret（用于HMAC-SHA1签名）
      ws-url: wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1  # 实时ASR WebSocket地址
      isv-url: https://api.xf-yun.com/v1/private/s1aa729d0              # ISV声纹识别API地址
      isv-group-id: ${XFYUN_ISV_GROUP_ID:default_group}                  # ISV声纹组ID（所有用户注册到此组）
      reconnect:                             # 断线重连配置
        max-retries: 5                       # 最大重连次数
        backoff-ms: 1000                     # 初始退避时间（毫秒）
        backoff-multiplier: 2.0              # 退避倍率（指数退避）
        max-backoff-ms: 30000                # 最大退避时间（毫秒）

  # ---------- 大模型配置 ----------
  llm:
    provider: ${LLM_PROVIDER:deepseek}       # 大模型提供商：doubao/qwen/deepseek
    api-key: ${LLM_API_KEY}                  # 大模型API Key
    api-url: ${LLM_API_URL}                  # 大模型API地址
    model: ${LLM_MODEL:deepseek-v4-pro}      # 模型名称
    timeout-seconds: 120                     # 调用超时（秒），纪要生成通常需要30-60秒
    max-retries: 2                           # 失败重试次数

  # ---------- 待办跟踪配置 ----------
  todo:
    remind:
      before-deadline-hours: [24, 2]         # 提前提醒时间点（小时）：截止前24h和2h各提醒一次
      max-delay-count: 0                     # 最大延期次数（0=不限制），超过后自动升级通知发起人
      min-remind-interval-hours: 2           # 最小提醒间隔（小时）：避免频繁提醒骚扰用户
      no-disturb-start: 22                   # 免打扰开始时间（小时，22=晚上10点）
      no-disturb-end: 8                      # 免打扰结束时间（小时，8=早上8点）
      daily-remind-time: "08:30"             # 每天定时提醒时间
    track:
      auto-delay-cron: "0 0 9 * * ?"         # 自动延期检查Cron：每天上午9点扫描逾期待办
      upgrade-notify-after: 0                # 升级通知阈值：提醒次数≥此值后通知发起人（0=不启用）

  # ---------- 飞书开放平台配置 ----------
  feishu:
    app-id: ${FEISHU_APP_ID}                 # 飞书应用App ID
    app-secret: ${FEISHU_APP_SECRET}         # 飞书应用App Secret
    verify-token: ${FEISHU_VERIFY_TOKEN}     # 飞书事件订阅Verification Token（Webhook验签）
    encrypt-key: ${FEISHU_ENCRYPT_KEY}       # 飞书消息加密Key（如开启加密模式）

    base-url: https://open.feishu.cn         # 飞书开放平台地址
    token-cache-seconds: 7200                # tenant_access_token缓存时长（秒，飞书默认2h）
    card-callback-timeout: 3000              # 卡片回调超时（毫秒），超时则飞书显示"操作失败"

# ============================================================
# XXL-Job 定时任务配置
# ============================================================
xxl:
  job:
    admin:
      addresses: ${XXL_JOB_ADMIN:http://localhost:8080/xxl-job-admin}  # 调度中心地址
    executor:
      appname: smart-meeting-executor        # 执行器名称（需与调度中心配置一致）
      port: ${XXL_JOB_EXECUTOR_PORT:9999}    # 执行器端口
      logpath: /data/logs/xxl-job            # 执行器日志路径
      logretentiondays: 30                   # 日志保留天数

# ============================================================
# 日志配置
# ============================================================
logging:
  level:
    root: INFO                               # 根日志级别
    com.smartmeeting: DEBUG                  # 项目包日志级别（开发环境DEBUG，生产INFO）
    com.smartmeeting.feishu: INFO            # 飞书API调用日志（减少噪音）
  file:
    path: /data/logs/smart-meeting           # 日志文件路径
    max-size: 100MB                          # 单文件最大大小
    max-history: 30                          # 保留天数
```

---

*文档结束 - v2.1-java - 2026-05-08*  
*变更记录：*
- *v2.0→v2.1: 15项查缺补漏修复——①int_user_mapping增加PRIMARY KEY ②int_meeting_participant增加联合唯一约束 ③状态机TRACKING歧义合并 ④补全ConfirmStatus/TodoStatus/Priority枚举 ⑤纪要→待办串行依赖设计 ⑥延时提醒改为Redis ZSet ⑦状态机流转规则表+previousMeetingId自动关联 ⑧待办责任人匹配逻辑+手动分配API ⑨音频存储格式规范 ⑩WebSocket断线重连机制+风险项 ⑪voiceprint增加feishu_user_id索引 ⑫(已完成) ⑬录音按钮飞书卡片 ⑭Voiceprint实体空行 ⑮飞书卡片回调API*
