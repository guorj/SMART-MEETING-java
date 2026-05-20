# 智能会议系统 — MCP + Skill 改造方案

> 日期：2026-05-20
> 分支：`feature/mcp-skill-refactor`
> 提交：`f8fdc74 增加mcp+skill模式`

---

## 一、改造背景

### 1.1 原始架构（方案B：CLI + 纯 Prompt）

Java 后端 `AiAgentService` 通过 `ProcessBuilder` 调用 `openclaw` CLI 命令触发 Agent 任务。Agent 接收一段手工拼接的 prompt 文本，自行判断如何访问外部数据（飞书多维表格、MySQL 等），输出分析结果。

**核心瓶颈：**

| 瓶颈 | 耗时 | 原因 |
|------|------|------|
| CLI 进程启动 | 3-5s | 每次 fork Node.js + 加载 openclaw 模块 |
| Agent 工具猜测 | 2-10s | prompt 文本描述"请通过可用工具读取飞书表格"→ Agent 自行尝试、可能失败 |
| prompt 过长 | LLM 推理慢 | 输出格式、约束规则、JSON 模板全写在 prompt 中（~500 tokens），每次重复拼接 |
| 调用不确定性 | 不稳定 | Agent 可能遗漏工具、误用工具、无法读取数据 |

### 1.2 改造目标

- **提速**：总耗时从 15-35s 降至 5-8s
- **降 Token 开销**：每次调用 Token 消耗净减 ~265 tokens
- **提高可靠性**：工具调用从"Agent 自行猜测"变为"MCP 确定性调用"
- **可回退**：通过 `openclaw.skill-mode` 配置一键切回旧方案

---

## 二、改造方案总览

改造分三个层次，按收益从大到小排列：

| 层次 | 改动内容 | 收益 | Token 影响 |
|------|---------|------|-----------|
| **L1 传输层** | CLI → Gateway HTTP API | 省 3-5s 进程启动开销 | 不变 |
| **L2 工具层** | 飞书多维表格 MCP Server + MySQL MCP Server | 消灭 Agent 工具猜测/重试（2-5s） | 增加 ~700 tokens（2个轻量 MCP Server 工具描述） |
| **L3 指令层** | Skill 替代冗长 prompt | prompt 更短 → LLM 推理更快（1-3s） | 从每次 ~500 tokens prompt → Skill ~800 tokens 按需加载 + prompt ~100 tokens |

**预估总效果：**

| | 改造前 | 改造后 |
|--|--------|--------|
| 总耗时 | 15-35s | **5-8s** |
| 上下文 Token | ~1,865/次（CLI 1,365 + prompt 500） | **~1,600/次**（MCP 700 + Skill 800 + 业务数据 100） |
| 可靠性 | Agent 自行猜测工具 | MCP 确定性调用 |

---

## 三、L1 传输层：CLI → Gateway HTTP API

### 3.1 改动内容

`AiAgentService.callAgent()` 从 `ProcessBuilder` CLI 子进程调用改为 OpenClaw Gateway HTTP API 调用。

**改造前（CLI）：**

```java
ProcessBuilder pb = new ProcessBuilder(
    "openclaw", "agent",
    "--agent", "JQClaw",
    "--message", prompt,
    "--timeout", String.valueOf(timeoutSeconds),
    "--json"
);
pb.redirectErrorStream(true);
pb.directory(new java.io.File(System.getProperty("user.home")));
Map<String, String> env = pb.environment();
env.put("OPENCLAW_PROFILE", "clone-boss");
env.put("OPENCLAW_STATE_DIR", "/home/alan/.openclaw-clone-boss");
Process process = pb.start();
// ... waitFor + readAllBytes + parseCliJsonResponse
```

**改造后（HTTP API）：**

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
if (authToken != null && !authToken.isBlank()) {
    headers.set("Authorization", "Bearer " + authToken);
}
Map<String, Object> body = new LinkedHashMap<>();
body.put("sessionKey", sessionKey);
body.put("message", prompt);
HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
String endpoint = gatewayUrl + "/api/v1/sessions/send";
ResponseEntity<String> resp = restTemplate.exchange(
    endpoint, HttpMethod.POST, request, String.class);
return parseAgentReply(resp.getBody(), taskType);
```

### 3.2 响应解析兼容

`parseAgentReply()` 同时支持两种格式，确保过渡期平滑：

- CLI 格式：`{"status":"ok","result":{"payloads":[{"text":"..."}]}}`
- HTTP 格式：`{"reply":"..."}` / `{"content":"..."}` / `{"message":"..."}` / `{"response":"..."}`

### 3.3 效果

- 省去每次 3-5s 的进程 fork + Node.js 加载开销
- Agent 会话在 Gateway 侧保持常驻，无需每次初始化
- MCP 工具连接由 Gateway 维护，不再需要每次重新建立

---

## 四、L2 工具层：MCP Server

### 4.1 架构原理

MCP（Model Context Protocol）是由 Anthropic 发起的开放标准协议（Linux Foundation 管理），让 AI Agent 通过标准化的 JSON-RPC 2.0 协议调用外部工具和数据源。

类比：**MCP 是 Agent 的"手"**——Agent 不需要知道飞书 API 怎么调、怎么鉴权、怎么翻页，只需要知道"有一个叫 `read_bitable_rows` 的工具，传入 `app_token` 和 `table_id`，就能拿到数据"。MCP Server 在背后做了所有脏活。

### 4.2 飞书多维表格 MCP Server

自定义 TypeScript MCP Server，提供 2 个工具：

| 工具 | 参数 | 说明 | Token 开销 |
|------|------|------|-----------|
| `read_bitable_rows` | `app_token`, `table_id`, `view_id`, `page_size`, `page_token` | 读取飞书多维表格记录列表 | ~100 tokens |
| `list_bitable_fields` | `app_token`, `table_id` | 获取字段列表（列名与类型） | ~100 tokens |

**实现要点：**

- 内置飞书 `tenant_access_token` 缓存（提前 5 分钟刷新）
- 错误处理：API 调用失败时返回 `isError: true` 标记
- 传输方式：stdio（与 Gateway 同机部署，延迟最低）

**源码位置：** `mcp-servers/feishu-bitable/index.ts`

### 4.3 MySQL MCP Server

使用官方预构建包 `@modelcontextprotocol/server-mysql`，提供 5 个工具：

| 工具 | 说明 | Token 开销 |
|------|------|-----------|
| `query` | 执行 SELECT 查询 | ~150 tokens |
| `list_tables` | 列出所有表 | ~80 tokens |
| `describe_table` | 查看表结构 | ~80 tokens |
| `insert` | 插入记录（谨慎使用） | ~100 tokens |
| `update` | 更新记录（谨慎使用） | ~90 tokens |

**部署方式：** `npx -y @modelcontextprotocol/server-mysql`，无需自定义代码。

### 4.4 MCP 配置

合并到 OpenClaw Gateway 的 `openclaw.json`：

```yaml
mcpServers:
  feishu-bitable:
    command: node
    args:
      - /opt/mcp-servers/feishu-bitable/dist/index.js
    transport: stdio
    env:
      FEISHU_APP_ID: ${FEISHU_APP_ID}
      FEISHU_APP_SECRET: ${FEISHU_APP_SECRET}

  meeting-mysql:
    command: npx
    args:
      - -y
      - "@modelcontextprotocol/server-mysql"
    transport: stdio
    env:
      MYSQL_HOST: ${DB_HOST}
      MYSQL_PORT: ${DB_PORT}
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: intelligence
```

**Token 开销总计：** ~700 tokens（2个轻量 MCP Server）

**配置参考文件：** `mcp-servers/openclaw-mcp-config.yml`

### 4.5 MCP Server 不推荐添加的重量服务

| MCP Server | 工具数 | Token 开销 | 是否推荐 |
|-----------|-------|-----------|---------|
| GitHub | 43+ | ~3,000 | **不推荐**——远超业务需要 |
| Puppeteer | 6 | ~600 | 不推荐——浏览器自动化比 API 调用慢 100 倍 |
| Slack | 5+ | ~300 | 暂不需要——无 Slack 集成场景 |

原则：**只配业务真正需要的 MCP Server，每个多余 Server 都在白白占上下文。**

---

## 五、L3 指令层：Skill 替代冗长 Prompt

### 5.1 MCP vs Skill 定位对比

| | MCP | Skill |
|--|-----|-------|
| **解决什么问题** | "连接"问题 — Agent 如何访问外部系统 | "方法论"问题 — Agent 如何执行特定任务 |
| **类比** | Agent 的"手"：触碰外部世界 | Agent 的"技能手册"：知道怎么做事 |
| **运作层面** | 工具/执行层：API、数据库、服务连接 | 指令/提示层：行为模式、工作流程、约束规则 |
| **加载方式** | 工具描述始终在上下文窗口（可启用 Tool Search 按需加载） | SKILL.md 按需加载（仅当任务触发时注入） |
| **Token 开销** | 常驻占用（每个 Server ~200-500 tokens） | 空闲 ~30 tokens，加载 ~800 tokens |

**两者互补：** MCP 提供工具连接，Skill 定义如何使用这些工具。

### 5.2 三个 Skill 文件

#### progress-analysis（上次待办进度分析）

```yaml
---
name: progress-analysis
description: "综合管理会会前进度通报：读取飞书多维表格与MySQL待办数据，交叉分析输出JSON"
allowed-tools:
  - feishu-bitable__read_bitable_rows
  - feishu-bitable__list_bitable_fields
  - meeting-mysql__query
---
```

核心流程：
1. 调用 `feishu-bitable__read_bitable_rows` 读取飞书多维表格
2. 调用 `meeting-mysql__query` 查询 int_meeting_todo 统计
3. 交叉对比两个数据源，标注矛盾项
4. 输出 JSON：progress_summary、delay_reasons、high_priority_alerts、recommendations、focus_items

**源码位置：** `skills/progress-analysis/SKILL.md`

#### matter-progress（事项进度通报）

```yaml
---
name: matter-progress
description: "录音页事项进度通报：读取飞书多维表格，输出Markdown进度通报正文"
allowed-tools:
  - feishu-bitable__read_bitable_rows
  - feishu-bitable__list_bitable_fields
---
```

核心流程：
1. 调用 `feishu-bitable__read_bitable_rows` 读取多维表格
2. 生成 Markdown 通报正文（概览 + 分项进度 + 风险 + 建议）

**源码位置：** `skills/matter-progress/SKILL.md`

#### minute-enhancement（纪要优化）

```yaml
---
name: minute-enhancement
description: "优化会议纪要：校验初版纪要质量，补全缺失信息，标注重点待办"
allowed-tools:
  - meeting-mysql__query
---
```

核心流程：
1. 调用 `meeting-mysql__query` 查询参会人信息辅助校验
2. 对比初版纪要与转写原文，校验关键信息
3. 输出 JSON：optimized_minute、quality_check、missing_info、speaker_summary、todo_highlight

**源码位置：** `skills/minute-enhancement/SKILL.md`

### 5.3 allowed-tools 白名单

每个 Skill 通过 `allowed-tools` 字段限定只使用必要的 MCP 工具：

| Skill | 允许的工具 | 限定原因 |
|-------|----------|---------|
| progress-analysis | feishu-bitable（2个）+ meeting-mysql（1个） | 进度分析只需读表格和查待办 |
| matter-progress | feishu-bitable（2个） | 通报只需读表格，不需要 MySQL |
| minute-enhancement | meeting-mysql（1个） | 纪要优化只需查参会人辅助校验 |

**效果：** `allowed-tools` 既限制了行为边界，也减少了上下文中需要加载的工具描述数量。

### 5.4 Java 端 Prompt 精简

`skillMode=true` 时，三个业务方法仅传递关键业务数据：

**改造前（analyzePreviousProgress）：**

```
【任务】分析上次会议待办进度，给出智能洞察和建议

【飞书多维表格-会前必读】当前会议命中「综合管理会」会前进度通报临时策略。
请你通过 **OpenClaw CLI / 会话内可用工具** 读取飞书多维表格 **「📋综合管理事项代办清单」** ...

上次会议：XX项目周例会
会议ID：meeting-001

待办统计：
- ⚠️ 已延期: 2项
- 🔄 进行中: 3项
- ✅ 已完成: 5项

请分析并输出以下内容（JSON格式）：
1. progress_summary: 进度概述（2-3句话）
2. delay_reasons: ...
3. ...
输出格式要求：{...}
```

约 **30 行 / ~500 tokens**，每次重复拼接。

**改造后（skillMode=true）：**

```
/skill:progress-analysis

meetingId=meeting-002
previousMeetingId=meeting-001
previousTitle=XX项目周例会
delayed=2
inProgress=3
completed=5
```

约 **7 行 / ~100 tokens**——输出格式、工具调用步骤、约束规则全由 SKILL.md 按需加载。

---

## 六、配置变更

### 6.1 application.yml 新增项

```yaml
# OpenClaw AI Agent 配置（方案C：HTTP API + MCP + Skill）
openclaw:
  enabled: ${OPENCLAW_ENABLED:false}
  gateway-url: ${OPENCLAW_GATEWAY_URL:http://127.0.0.1:18792}
  agent-session-key: ${OPENCLAW_SESSION_KEY:...}
  timeout-seconds: ${OPENCLAW_TIMEOUT:60}
  auth-token: ${OPENCLAW_AUTH_TOKEN:...}

  # --- 新增：MCP + Skill 模式开关 ---
  skill-mode: ${OPENCLAW_SKILL_MODE:true}

  # --- 旧配置：逐步弃用 ---
  comprehensive-bitable-progress-enabled: ...
  comprehensive-bitable-preset-codes: ...
  # 注意：skill-mode=true 后，飞书多维表格改由 feishu-bitable MCP Server 直接读取
```

### 6.2 AiAgentService 新增字段

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `transport` | `http` | 传输方式：http（推荐）或 cli（旧方案） |
| `cliProfile` | `clone-boss` | CLI 专属配置（保留兼容） |
| `cliStateDir` | `/home/alan/.openclaw-clone-boss` | CLI 专属目录（保留兼容） |
| `skillMode` | `true` | Skill 触发模式：true（推荐）/ false（兼容旧方案） |

### 6.3 渐进式切换策略

| 配置组合 | 效果 | 适用阶段 |
|---------|------|---------|
| `skill-mode=false` + CLI ProcessBuilder | 旧方案，完整 prompt | 验证 HTTP API 基本连通性 |
| `skill-mode=false` + HTTP API | HTTP 调用 + 旧 prompt（省进程启动） | 过渡期第一步 |
| `skill-mode=true` + HTTP API + MCP | **推荐方案**（完整 MCP+Skill） | 正式上线 |

通过一个配置开关 `openclaw.skill-mode` 即可随时切回旧方案，零风险上线。

---

## 七、文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `AiAgentService.java` | Java（修改） | callAgent 从 CLI → HTTP API；3个业务方法增加 skillMode 分支 |
| `application.yml` | 配置（修改） | 新增 openclaw.skill-mode 配置项 |
| `mcp-servers/feishu-bitable/index.ts` | TypeScript（新增） | 飞书多维表格 MCP Server |
| `mcp-servers/feishu-bitable/package.json` | JSON（新增） | MCP Server 依赖声明 |
| `mcp-servers/feishu-bitable/tsconfig.json` | JSON（新增） | TypeScript 编译配置 |
| `mcp-servers/openclaw-mcp-config.yml` | YAML（新增） | OpenClaw MCP Server 配置参考 |
| `skills/progress-analysis/SKILL.md` | Markdown（新增） | 上次待办进度分析 Skill |
| `skills/matter-progress/SKILL.md` | Markdown（新增） | 事项进度通报 Skill |
| `skills/minute-enhancement/SKILL.md` | Markdown（新增） | 纪要优化 Skill |

---

## 八、部署步骤

### 8.1 构建飞书 MCP Server

```bash
cd /opt/mcp-servers/feishu-bitable
npm install
npm run build
```

### 8.2 配置 OpenClaw Gateway

将 `mcp-servers/openclaw-mcp-config.yml` 中的 MCP Server 配置合并到 `~/.openclaw/openclaw.json` 的 `mcpServers` 字段中。

### 8.3 部署 Skill 文件

将 `skills/` 目录下的三个 Skill 复制到 OpenClaw 的 skills 目录（通常为 `~/.openclaw/skills/` 或项目仓库 `.claude/skills/`）。

### 8.4 重启 Gateway

```bash
openclaw gateway restart
openclaw mcp list  # 验证 MCP Server 注册成功
```

### 8.5 环境变量

```bash
# 必须设置（MCP Server 需要）
FEISHU_APP_ID=cli_xxxxxxxx
FEISHU_APP_SECRET=xxxxxxxxxxxx

# 已有配置，无需变更
OPENCLAW_ENABLED=true
OPENCLAW_GATEWAY_URL=http://127.0.0.1:18792
OPENCLAW_AUTH_TOKEN=bc6e1fc38ed4d2be39556be74ce349ea376354f455089e20

# 新增配置
OPENCLAW_SKILL_MODE=true  # 默认 true，可设 false 切回旧方案
```

### 8.6 验证步骤

1. 先设 `OPENCLAW_SKILL_MODE=false` 跑旧方案，确认 HTTP API 调用正常
2. 切换 `OPENCLAW_SKILL_MODE=true`，验证 Skill 加载和 MCP 工具调用
3. 对比两种模式的输出质量和耗时

---

## 九、MCP 通信原理

### 9.1 Agent 与 MCP Server 的交互

Agent 通过 JSON-RPC 2.0 协议与 MCP Server 通信（stdio 传输）：

**Agent 发请求：**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "read_bitable_rows",
    "arguments": {
      "app_token": "PjL2b6sPBa9UESsVhhJcMbwSnmc",
      "table_id": "tblFH8QdCzw1RK3m",
      "view_id": "vewM1Y9Vem",
      "page_size": 100
    }
  }
}
```

**MCP Server 返回：**

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "{\"code\":0,\"data\":{\"items\":[{\"fields\":{\"事项\":\"XX项目验收\",\"状态\":\"延期\",\"负责人\":\"张三\"}},...]}}"
      }
    ]
  }
}
```

### 9.2 性能数据

| 方案 | 单次工具调用延迟 | Token 消耗 | 可靠性 |
|------|----------------|-----------|--------|
| 直接 API（进程内） | 1.8-2.8ms | 203 tokens | 最高 |
| MCP（JSON-RPC/stdio） | 3.3-4.6ms | 461 tokens | 高 |
| CLI 子进程（旧方案） | 26.9-28.8ms | 1,365 tokens | 中 |

MCP 的协议开销仅 ~3ms，相对于上游飞书 API 50-500ms 响应时间几乎可以忽略。

### 9.3 安全考虑

- **凭据隔离**：飞书 `APP_ID/APP_SECRET`、MySQL 密码仅在 MCP Server 的 `env` 中，Agent 本身不持有这些凭据
- **传输安全**：stdio 传输仅限本机，无网络暴露
- **权限限定**：Skill 的 `allowed-tools` 白名单限制了 Agent 可调用的工具范围
- **审计合规**：每次 MCP 工具调用可在 Gateway 侧记录审计日志

---

## 十、风险与待办

### 10.1 当前风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| MCP Server 进程崩溃 | Agent 工具调用失败 | Gateway 自动重启 MCP Server；Skill 约束"若无法读取则首句标注" |
| 飞书 API 限频 | 多维表格读取失败 | MCP Server 内置 token 缓存，避免重复鉴权；可增加重试逻辑 |
| MySQL MCP Server 无只读限制 | Agent 可能执行写操作 | 生产环境建议使用只读 MySQL 账号；后续可自定义 MCP Server 限制只允许 SELECT |
| OpenClaw Gateway 版本兼容 | HTTP API 格式可能不同 | `parseAgentReply()` 兼容多种响应格式 |

### 10.2 后续待办

| 待办 | 优先级 | 说明 |
|------|--------|------|
| 弃用 `OpenclawComprehensiveBitableBranch` | 中 | Skill 模式下不再需要 prompt 文本描述飞书表格，此组件可逐步移除 |
| 弃用 `feishuMultitableDirective` 参数 | 中 | `analyzePreviousProgress` 的最后一个参数在 Skill 模式下不使用 |
| MySQL MCP Server 只读限制 | 高 | 生产环境需确保 Agent 只能 SELECT，不能 DELETE/UPDATE |
| MCP Server 健康检查 | 低 | 在 `isAgentAvailable()` 中增加 Gateway 连通性检测 |
| MCP Tool Search 启用 | 低 | OpenClaw 4.24+ 支持动态工具发现，减少上下文占用 |
| 升级 macOS git 版本 | 低 | 当前 git 2.24.3 不支持 `--trailer`，影响 Cursor IDE 提交体验 |