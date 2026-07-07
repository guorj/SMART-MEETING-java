方舟众测平台
👤

+
新建任务
这是目前我在做的项目，已经进入收尾阶段。目前人手不足，无法完全测试，请你帮我进行...
D6ZHIQ
📈
数据统计
tempest
raptor
umbra
saber
执行轨迹
生成产物

📥
下载产物

＋
追加一轮
产物反馈
*
产物效果截图
请下载到产物到本地执行验收，上传验收过程的相关截图，并描述验收过程中存在的问题

+
上传图片
* 产物质量
未评分
请输入评论（必填）
提交反馈
* 交付效率
未评分
请输入评论（必填）
提交反馈
* 模型的综合表现怎么样
未评分
请输入评论（必填）
提交反馈
轨迹分析
对「执行轨迹」和「生成产物」中，有明显亮点或问题的地方进行圈选，点击评论图标，即可填写对应的分析评价
TEST_REPORT.md
×
# 智能会议系统（Java 版）测试报告

**测试日期**：2026-07-05  
**测试环境**：Linux x64, OpenJDK 17.0.16, Maven 3.9.9, H2(MODE=MySQL) in-mem fallback  
**代码版本**：git HEAD (v0.35.0-feishu-vc-recording 之后)

---

## 1. 总览

| 模块 | 测试数 | 通过 | 失败 | 错误 | 结果 |
|------|--------|------|------|------|------|
| meeting-config-core | 31 | 31 | 0 | 0 | ✅ 全绿 |
| matter-progress-core | 48 | 48 | 0 | 0 | ✅ 全绿 |
| meeting-admin-server | 1 | 1 | 0 | 0 | ✅ 全绿 |
| meeting-server | 266 | 266 | 0 | 0 | ✅ 全绿 |
| **合计** | **346** | **346** | **0** | **0** | **✅ BUILD SUCCESS** |

启动冒烟：
- `meeting-admin-server-0.1.0.jar`：Spring 上下文 4.2 秒启动成功
- `meeting-server-0.1.0.jar`：Spring 上下文加载成功（无外部 MySQL 时数据库连接失败符合预期）

---

## 2. 发现并修复的缺陷

### 2.1 生产代码缺陷（已修复）

| # | 位置 | 问题 | 修复 |
|---|------|------|------|
| B1 | `JwtUtil.java` | 默认密钥 `"change-me-in-production"` 仅 23 字节，HMAC-SHA256 要求 ≥ 256 位（32 字节），会直接抛 `WeakKeyException`，未覆盖默认值部署直接崩溃 | 1. 默认密钥改为 64 字节长字符串；2. `getSigningKey()` 对短密钥自动 SHA-256 派生 32 字节（兼容历史部署） |
| B2 | `OabpAgendaTaskQueryService.formatCell()` | `Time → LocalTime.toString()` 在秒数为 `00` 时会把 `"09:15:00"` 截断成 `"09:15"`，导致 OA/BPM 字段时间展示不一致 | 改用 `DateTimeFormatter.ofPattern("HH:mm:ss")` |
| B3 | `FeishuMeetingStartCoordinator.createMeetingStartAndNotifyFeishu()` | `/feishu-web/create-and-start` 返回的 `recordingUrl` 与飞书卡片 URL 不一致：卡片 URL 带 `&autostart=1` 但接口响应从 DB `recordingUrl`（废弃列）读取，不带 autostart | API 响应层 `setRecordingUrl(autostartUrl)` 覆盖，保证前端进入即自动开始；同时停止向已废弃的 `recording_url` 列写入（JWT token 长度已超 500 字符，VARCHAR(500) 会溢出） |
| B4 | `FeishuMeetingStartCoordinator.returnExistingMeeting()` | 复用草稿分支未给响应 `recordingUrl` 写 autostart，同样存在卡片-API 不一致 | 同上，统一用 `resp.setRecordingUrl(draftUrl)` 覆盖 |

### 2.2 测试基础设施缺陷（已修复）

| # | 位置 | 问题 | 修复 |
|---|------|------|------|
| T1 | `meeting-server/src/test/resources/test-schema-h2.sql` | H2 回退测试 schema 过期：缺少 `vc_meeting_url / vc_minute_token / vc_recording_url`、`int_pipeline_*`、`int_meeting_todo_progress / _attachment / _audit`、`int_weekly_matter_comparison_*` 等表/列，导致约 22 个集成测试因 SQL 错误失败 | 重写 H2 schema 对齐当前生产 DDL（schema.sql + v0.17-v0.35 升级脚本） |
| T2 | `meeting-admin-server/src/test/resources/application-test.yml` | 含 `spring.profiles.active: test`，Spring Boot 2.4+ 禁止在 profile 专用文件里再设 `spring.profiles.active`，导致 `SystemConfigDescriptorTierTest` 启动失败 | 删除该非法属性，显式 `spring.autoconfigure.exclude` 掉 DataSource/MyBatis |
| T3 | `HostAgendaJsonCodecLocalTest.roundTripShowInHostOnDoc` | 断言 `json.contains("\"showInHost\":false")` 依赖 pretty printer 不留空格（Jackson 默认会加空格成 `"showInHost" : false`），属字符串细节耦合 | 改为同时校验 `showInHost` 与 `false` 子串，并保留核心语义断言（parse 后 isShowInHost 必须为 false） |
| T4 | `MeetingHostMediaTeardownServiceTest` | 少 mock 了新依赖 `RecordingService`，运行时 NPE | 新增 `@Mock RecordingService recordingService;` |
| T5 | `CalendarAttendeeResolverTest` | 仅 stub 了 `resolve("b7319b67")`，但被测代码还会对已解析出的 `8813018f / uid-tian` 归一化再 resolve 一次，触发 Mockito strict stubbing 失败 | 补充对已解析 ID 的 stub |
| T6 | `RecorderPageStaticSmokeTest.staticHostAvatarPreviewOk` | `/static/**` 资源响应头未声明 charset，MockMvc 按 ISO-8859-1 解码字节导致中文断言乱码 | 改为取原始字节按 UTF-8 解码后断言 |
| T7 | `OpenClawMcpProviderTest` 6 个用例 | 测试依赖 `application-dev.yml`，但 meeting-server 模块缺少该文件导致 `OpenClawDevConfigLoader.load()` 直接抛异常；且我一开始写的 dev yml 误把 `openclaw.enabled` 设为 false | 新增 `src/test/resources/application-dev.yml` 与测试预期对齐（enabled=true、gateway-url=18789、skill-mode=true、timeout=120、session-key 与断言一致） |
| T8 | `TodoApiTest` 6 个用例 | TodoController 所有端点要求 `@RequestParam("token")` Dashboard JWT，但旧测试未带 token 触发 400/403 | 注入 `JwtUtil / DashboardGrantService / JdbcTemplate`，在 `@BeforeEach` 生成 token、写入白名单配置（defaultDeny=false + user-a 授权），所有请求加 `.param("token", dashboardToken)` |
| T9 | `MeetingControllerTest.testFeishuWebCreateAndStart` | 1) 真实调 `FeishuService.getTenantToken()` 无凭据抛异常；2) 测试期望 status=STARTED 与实际代码契约不符（协调器仅建草稿，STARTED 由前端 autostart 触发）；3) 断言 `recordingUrl` 含 `autostart=1` 暴露了 B3 | 1. `@MockBean FeishuService` stub 用户名/卡片发送；2. 断言调整为 `ISSUE_COLLECTING` + `recordingUrl` 含 autostart（修正后现在通过） |

### 2.3 其他发现（需跟进但未在本次修复）

- **`application.yml` 缺失**：`meeting-server/src/main/resources/` 没有 `application.yml`，仅靠 `@ConfigurationProperties` 默认值 + `application-dev/test.yml`（仅测试资源）。生产部署完全依赖环境变量注入，README 与 `.env.example` 有列举但建议补一个带注释的默认 `application.yml`。
- **README 文档死链**：README 中 `docs/PRD-Java-二期.md`、`docs/coding-standards.md`、`docs/数据库表结构冗余与字段合理性分析.md` 三个文件不存在，应删除或补齐。
- **数据库表结构说明**：`docs/数据库表结构说明.md` 未包含 v0.17 新增的 pipeline/outbox 表、v0.22-v0.23 的 todo 审计/进度/拆分/审批字段、v0.26 周对比、v0.35 VC 录制 URL 字段；建议下次 schema 变更时同步。

---

## 3. 构建命令

```bash
export JAVA_HOME=/path/to/jdk-17
./mvnw -B -DskipTests clean package          # 打包 4 个 jar
./mvnw -B -DskipTests=false test              # 跑 346 个单元测试
./mvnw -B -DskipTests=false test -Dtest='!*LiveTest'   # CI 跳过需要外部服务的用例
```

CI 稳定运行耗时约 25-35s（本地 Maven 本地仓已预热）。

---

## 4. 建议的上线前检查项

1. **生产密钥**：务必通过环境变量 `MEETING_JWT_SECRET` 设置 ≥32 字节密钥；当前已兜底 SHA-256 派生但显式配置更稳妥。
2. **`int_meeting.recording_url` 列**：已停止写入，保留只读兼容。已有历史行不会受影响；如需节省空间可后续做数据迁移。
3. **飞书 Web 创建入口**：修复后 API 返回的 `recordingUrl` 带 `&autostart=1`，前端/飞书卡片点击即会触发开始会议；请确认前端对该参数的自动开始流程已实现（前端 `recording-page.js` 已读取 `autostart=1`）。
4. **OA 时间字段**：修复后 `TIME` 类型字段固定返回 `HH:mm:ss`（如 `09:15:00`），若下游期望省略秒需在前端处理。



