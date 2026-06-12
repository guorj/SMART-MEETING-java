# 智能会议系统 - 用户手册（精简版）

文档版本：2026-06-08（与当前代码实现对齐）

---

## 1. 当前系统现状

- 主服务：`meeting-server`（建会、录音、主持、纪要、待办）。
- 辅助服务：`feishu-scheduled-bot`（会前对比等定时能力）。
- 会议类型：统一按 `presetTypeCode > 0` 作为模板会处理。
- 会序资料：模板权威在 `int_meeting_type_preset.host_agenda`，建会时写入 `int_meeting.host_agenda` 快照。
- 主持页读取：会中优先读取 `int_meeting.host_agenda`；支持展示 `docs[].role=SOURCE/BOTH/OUTPUT`。

---

## 2. 关键业务流程

1. 飞书发送「会议管理」进入工作台（**须在工作台白名单** `dashboard.user_grants` 内；未授权时单聊提示联系管理员）。
2. 选择模板创建会议（需 `canCreateMeeting=true`）。
3. 建会时按模板最新 `host_agenda` 生成会议快照。
4. 进入**会议主页**（`/rec` / `/host`）后，在 **会议控制** 区点击「开始会议」开启会话与录音（**每场仅首次有效**；刷新页面后会自动恢复主持控制，**须点「重连录音」**重新授权麦克风，不会再次调用开始接口）。**主控**：先 `start` 者得写权限；**旁观**：使用「复制旁观链接」或 `/view/{meetingId}`，只读会序/资料/倒计时。
5. 结束会议请使用 **会议控制** 底部独立的「结束会议」按钮（与「下一议题」等操作分离），避免误触。
6. 结束会议后按开关触发会后链路：离线转写（`meeting.asr.offline-enabled`）与纪要生成（`meeting.minute.generation-enabled`）**相互独立**；两者皆开时先离线转写入库，再生成纪要。**开关维护**：Admin → **系统参数**（热加载）。生效顺序：**DB > Java 出厂默认 > infra YAML/env**；新装/空库须执行 `schema-seed/meeting-runtime-defaults-prod.sql`（生产策略）；Admin「恢复默认」回 Java 出厂值，非 seed 值。

### 2.0.1 前台与后台 UI（2026-06-10 ui-kit）

前后台共用 **`ui-kit/`**（Swiss 浅色 + Iconsax Linear 图标 + Shimmer/BlurFade 动效），静态资源版本号 `sm-ui-20260610-2`。修改设计令牌后运行 `ui-kit/sync-ui-kit.ps1` 同步到 `meeting-server` 与 `meeting-admin-server`。

| 区域 | 说明 |
|------|------|
| **工作台**（`/dashboard`） | 创建会议、恢复/结束进行中会议、声纹注册、近期会议列表；状态以中文 Badge 展示（录音中/进行中/纪要生成中等） |
| **会议控制** | 开始/暂停/继续、全宽**离线录音状态条**（红点 + 计时）、下一议题/跳过/加时、议题与会议倒计时 |
| **当前议题资料** | 当前会序绑定的飞书文档/本地上传资料 |
| **AI 主持** | 虚拟主持形象与 TTS 播报状态 |
| **议程列表** | 全部会序项及进度 |

#### 前端 UI 预览（免完整会中链路）

调主持页前台 UI（会议控制、会序资料、AI 主持 Strands、议程列表等）时，可用以下方式，**不必每次重启 meeting-server**：

| 方式 | 适用场景 | 操作 |
|------|----------|------|
| **预览页** | 全页 UI / avatar / CSS 迭代 | 服务已启动时打开 `http://{host}:{port}/static/host-avatar-preview.html`；底部可切换「未开始 / 进行中 / 检点 / 播报 / 旁观」等 mock 场景，无需 token |
| **本地静态服务** | 最快 CSS 迭代（零 JVM） | 在 `meeting-server/src/main/resources` 执行 `npx serve -l 5500`，浏览器打开 `http://localhost:5500/static/host-avatar-preview.html`，改 `static/styles/host-meeting.css` 后普通刷新 |
| **完整主持页强刷** | 联调会序资料等全页 | 确保 IDE 已将 `src/main/resources/static` 同步到 `target/classes`（或 `mvn compile -pl meeting-server`），在 `/host/{meetingId}?token=...` **Ctrl+F5**；主持 HTML 为 `no-store`，CSS/JS 靠 `?v=sm-ui-*` 破缓存 |

说明：项目未引入 `spring-boot-devtools`；若用 `java -jar` 跑 fat jar，改静态文件后需重新打包或重启。`host-strands-avatar.js` 经 esm.sh 加载 `ogl`，预览环境需能访问外网。

### 2.0 工作台权限与多人前台（2026-06-08）

- **白名单**：存储于 `int_meeting_system_config.config_key=dashboard.user_grants`；Admin **用户管理 → 编辑用户 → 前台授权** 维护；页头可配置「默认拒绝未授权用户」。
- **权限项**：`canCreateMeeting`（建会/恢复）、`canEndMeeting`（结束会）、`canRegisterVoiceprint`（声纹注册）；白名单内默认人人可注册声纹。
- **会中入口**：飞书「会议管理」在当前会话（群聊或单聊）直接发送「智能会议前台」交互卡片。
- **主控 + 旁观**：每场会议 1 路录音推流；主持写 API 仅主控 token；旁观 token（`type=viewer`）可读 state/agenda，不可操作下一议题或结束会。

### 2.1 录音与转写（默认）

- **默认**：`meeting.asr.realtime-enabled=false`，会中仅浏览器 WebSocket 写入单场 `{meetingId}.pcm`，无实时字幕；**会议控制**区内显示全宽**离线录音状态条**（红点 + 计时 + 说明文案），而非独立字幕区。浏览器麦克风仅采集本机物理输入（现场声音），**无法采集飞书会议中远程参会人的语音**；远程参会人音频需通过飞书 VC 云端录制回调获取（详见 `feishu-vc-recording-design.md`）。
- **会后**：无会中定稿转写且 `offline-enabled=true` 时，系统对 PCM 做离线转写（IST 说话人分离，参会人 ≥2 声纹时优先 roleType=3）+ balanced ISV 标注（簇级投票 → 未命名簇按段补标 → 簇内分裂），结果写入 `int_transcript_segment`；纪要生成（若开启）从 DB 读取该转写，不再内联触发离线 ASR。
- **可选**：将 `realtime-enabled` 设为 `true` 可恢复会中实时转写与字幕；此时纪要以会中分段为准，不覆盖为离线结果。
- **音频源策略**：会议可能产生两份录音文件——浏览器麦克风 PCM（现场声音）与飞书 VC 云端录制（线上声音）；离线 ASR 按场景选择最优源，详见 `feishu-vc-recording-design.md` §4。
- 会前建议完成参会人声纹注册（见飞书「声纹注册」入口），详见 `开关手册.md` §6.2。
- 纪要链路各步骤（离线 ASR、LLM 初稿、AI 增强、飞书文档、通知等）可独立开关，详见 `开关手册.md` §3.1、§6.6。

---

## 3. 会序资料机制（重点）

### 3.1 为什么会有快照

- 允许同模板下的不同会议做独立回填与微调。
- 会议进行中不受模板实时变更干扰。

### 3.2 快照何时更新

- 建会时：`createMeeting` 写入会议 `host_agenda`。
- 会前回填提交时：按 meeting 维度更新 `host_agenda`。
- 内部刷新接口：`/api/v1/internal/meetings/refresh-host-agenda` 可重建快照。

### 3.3 本地上传资料（2026-06-07 起）

- 管理后台「会序与资料」中，每条资料绑定支持 **飞书链接** 或 **本地上传**（doc/docx + 常见图片）。
- 上传后管理端卡片内即时预览；建会后主持页切换议题时可查看图片或下载文档。
- 文件存储于 `meeting.agenda-material.storage-dir`（默认 `./data/agenda-materials`），admin-server 与 meeting-server 须共用同一路径。

### 3.4 主持页结构化资料展示（2026-06-10 起）

主持页左侧「当前议题资料」通过 `GET /api/v1/meetings/{id}/agenda-doc-content` 拉取正文，按 `contentType` 内嵌渲染：

| contentType | 来源 | 说明 |
| ----------- | ---- | ---- |
| `docx_blocks` | 飞书 docx/wiki、本地上传 docx | 标题/列表/图片块；失败降级 plainText |
| `bitable_records` | 飞书 base/wiki 多维表格 | 支持 GROUPED 分区、多数据表（`tables[]`）、进度条/链接等类型化单元格 |
| `task_list` | 飞书任务清单 AppLink（`applink.../client/todo/task_list?guid=`） | Task v2 API 拉取清单与任务；需应用权限 `task:tasklist:read`，且**必须**在飞书任务清单「成员」中将本应用添加为可阅读协作成员（否则返回 1470403/403） |
| `sheet_cells` / `excel_workbook` | 飞书 wiki 电子表格、本地 xls/xlsx | 多 sheet 时可能为 `excel_workbook` |
| `ppt_slides` / `pdf_pages` | 本地 ppt/pdf、Wiki slides（导出 PDF） | 翻页 + 缩略图条；PDF 可切换「连续滚动」 |
| `image_gallery` | 本地上传图片（并入 `parts[]`） | 画廊展示，点击 Lightbox 放大 |
| `html` | 本地 doc/docx 降级预览 | DOMPurify 消毒后内嵌 |
| `sheet_cells` | 本地 `.csv`、飞书 `/sheets/` 与 Wiki sheet | 表格内嵌 |
| 图片 | 本地图片、doc 内插图 | 点击任意图片进入全屏 Lightbox（滚轮缩放、ESC 关闭） |

**Wiki 扩展节点（0.36）**：`file`（按附件类型走 pdf/ppt/xlsx 管线）、`slides`（异步导出 PDF 后分页预览）、`mindnote`（API 无正文，展示标题 + 外链提示）。

**任务清单 AppLink**：会序可配置 `https://applink.feishu.cn/client/todo/task_list?guid={清单GUID}`。若出现 **1470403 / Invoker is unauthorized**，表示应用未被加入该清单协作成员：在飞书客户端打开清单 → 成员 → 添加应用为可阅读成员，并在开放平台确认 `task:tasklist:read` 已开通。拉取失败时主持页展示 `fetchError` 与外链；若无法开通，可改用 `/wiki/`、`/docx/`、`/base/` 或本地上传 xlsx/csv/pdf。

- 图片代理：`/agenda-materials/proxy-image`（飞书）、`/agenda-materials/proxy-file-image`（本地页图）。
- 拉取失败时仍展示飞书外链与 `fetchError` 说明，不阻塞主持流程。
- `parts[].images[]`：docx 内嵌图代理 URL 列表，主持页加载资料时会预取，加快首屏显示。

### 3.5 发版后验证清单（约 10 分钟）

部署 `meeting-server` 静态资源更新后，任选一场含资料的会议主持页：

1. **Network**：`GET /api/v1/meetings/{id}/agenda-doc-content` 响应中 docx part 含 `contentType` + `structuredContent`；含图时 `images[]` 非空。
2. **Network**：`GET .../agenda-materials/proxy-image?imageKey=` 返回 200，响应头含 `Cache-Control`。
3. **DOM**：资料区出现 `.structured-docx-blocks`、`.structured-data-table` 或 `.structured-slide-deck`（按资料类型）。
4. **交互**：点击 doc 内图片或本地 gallery 图片 → 全屏 Lightbox；滚轮缩放；ESC 关闭。
5. **Sheet/Excel**：合并单元格表格 rowspan/colspan 正确（若源表有合并）。
6. **降级**：刻意断网或无效链接时仍有外链与 `fetchError`，主持流程可继续。

### 3.6 常见误区

- 修改模板后，已建会议不会自动跟随模板变化。
- 新建会议若资料为空，优先检查新会议的 `int_meeting.host_agenda` 是否已带 `docs[]`。
- `/base/` 链接无 `table=` 时也可拉取全库多维表格（结构化返回 `tables[]`）。

---

## 4. 已知行为与限制

- 待办提醒若未关闭且责任人是无效 `user_id`（如 `vp_...`），会触发飞书 400。
- 定时任务是否执行取决于开关与 profile 覆盖值，详见 `开关手册.md`。
- **临时兜底映射（2026-06-07 起）**：当飞书回调中某用户的 `user_id` 为空时，系统按 `open_id` 自动映射临时 userId，使该用户可正常使用会议管理功能。命中时日志打印 `user_id empty, apply temp open_id fallback`。此映射为临时措施，待飞书侧修复用户身份问题后移除。

---

## 5. 常见问题排查

### 5.1 主持页看不到会序资料

按顺序检查：

1. `int_meeting.host_agenda` 是否有 `docs[]`。
2. 主持启动日志里 `docBoundTopics` 是否大于 0。
3. 当前会序是否配置了可访问飞书链接（`docx/wiki/base`）。

### 5.2 关闭待办后仍有提醒调用

- `meeting.todo.extraction-enabled` 只控制提取，不控制提醒调度。
- 需同时关闭 `meeting.todo.reminder-enabled`。

### 5.3 “会议管理”无弹窗

按顺序检查：

1. 用户是否在工作台白名单（`dashboard.user_grants`）且 `enabled=true`；未授权时仅收到文字拒绝提示。
2. 飞书回调是否上送 `user_id`（日志 `user_id empty, apply temp open_id fallback` 表示命中临时兜底）。
3. `meeting-server` 日志是否有 `发送会议前台入口卡片失败` 或飞书 API 报错（如 `meeting.base-url` 不可达）。

---

## 6. 运维最小建议

- 发布前确认当前 profile 的 `application-*.yml` 覆盖值；**业务开关**（pipeline、会前调度、ISV 阈值等）以 **Admin 系统参数 + DB** 为准，勿在 `application-dev.yml` 改 `meeting.pipeline.*` / `scheduler.pre-*` / `isv.enabled`（dev 中此类块经 `MeetingRuntimeConfigLoader` 后不持久生效，见 [config-ranges.md §YAML 必要性审计](./config-ranges.md#yaml-必要性审计meeting-server)）。
- 配置优先级：**DB（Admin 热更）> Java 出厂默认 > profile YAML > `application.yml` 基线**；冷启动 16 项改 yml/env 后须重启。
- 涉及调度器开关变更后必须重启服务。
- 生产环境建议显式配置所有关键开关，避免依赖默认值。

---

## 7. 相关文档

- `README.md`：文档总入口。
- `开关手册.md`：开关全量说明与运维组合。
- `config-ranges.md`：取值范围与 [YAML 必要性审计（meeting-server）](./config-ranges.md#yaml-必要性审计meeting-server)。
