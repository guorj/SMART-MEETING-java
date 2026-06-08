# 会前对比 — OpenClaw Gateway 运维检查清单

> 对应 Bot 默认 `delegate-to-mcp=true`。配置参考：[openclaw-mcp-config.yml](openclaw-mcp-config.yml) · 工具表：[LARK-MCP-TOOLS.md](LARK-MCP-TOOLS.md) · 业务文档：[weekly-matter-comparison.md](../docs/weekly-matter-comparison.md)

## 1. 启动前（一次性 / 变更后）

- [ ] **安装 Skill `matter-progress`**：将 `smart-meeting-java/skills/matter-progress/` 部署到 JQClaw workspace：
  `mkdir -p /root/.openclaw/workspace/JQClaw/skills/matter-progress`
  并复制 `SKILL.md`（或 `openclaw skills install <path> --as matter-progress`）。
- [ ] **合并 MCP 配置**：将 `openclaw-mcp-config.yml` 中 `mcp.servers.lark-mcp` / `meeting-mysql` 写入生产 `openclaw.json` 的 **`mcp.servers`**（勿用顶层 `mcpServers`）。
- [ ] **`lark-mcp` token 模式**：`args` 含 `--token-mode` + **`tenant_access_token`**（与仓库参考一致）。**禁止**对会前对比任务使用 `user_access_token` 或 CLI `--oauth`（会导致 Agent 用用户 token 读表失败、Secret 截断重试、回退陈旧 MySQL 数据）。
- [ ] **应用凭证**：`-a` / `-s` 与 Bot `feishu.weekly-comparison.app-id` / `app-secret` 为**同一** weekly-comparison 飞书应用。
- [ ] **OpenAPI 白名单 `-t`** 至少包含：
  ```
  bitable.v1.appTableField.list,bitable.v1.appTableRecord.search,
  docx.v1.document.create,docx.v1.documentBlockChildren.create,
  docx.v1.document.rawContent,wiki.v2.space.getNode
  ```
- [ ] **`meeting-mysql` 包名**：`npx -y mcp-server-mysql`（**非** `@modelcontextprotocol/server-mysql`，后者 npm 404）。
- [ ] **`meeting-mysql` 环境变量**：`MYSQL_PASS` / `MYSQL_DB`（本包不用 `MYSQL_PASSWORD` / `MYSQL_DATABASE`）。
- [ ] **`meeting-mysql` 连通**：`MYSQL_HOST` / 库名与 Bot、meeting-server **共库**（`intelligence`）；生产建议只读账号 + Skill 仅放行 `mysql_query`。
- [ ] **WS 鉴权**：Bot `feishu.weekly-comparison.openclaw.gateway-url` + `auth-token`（或 `device-token`）与 Gateway 一致；`timeout-seconds` ≥ **300**。

## 2. 部署后验证

```bash
# 替换为实际 profile
openclaw --profile <profile> mcp list
openclaw --profile <profile> mcp show lark-mcp
openclaw --profile <profile> mcp show meeting-mysql
openclaw --profile <profile> skills list
```

- [ ] `skills list` 含 **`matter-progress`**（无则 Bot 下发 `/skill:matter-progress` 会报 `Unknown skill`）。
- [ ] `mcp list` 可见 `lark-mcp`、`meeting-mysql`（**仅注册 ≠ 进程已启动**）。
- [ ] `mcp show meeting-mysql` 列出 **`mysql_query`**（若空或报错，查 Gateway 日志 `failed to start server "meeting-mysql"`）。
- [ ] `mcp show lark-mcp` 显示 **`token_mode: tenant_access_token`**（或等价字段），**非** `user_access_token` / `auto`。
- [ ] 重启 Gateway 后，Bot 启动日志含：`weekly-comparison pipeline=OpenClaw-MCP`。
- [ ] 试跑：`POST /api/weekly-comparison/jobs/{id}/execute` → `last_run_status=SUCCESS`，preset `host_agenda` 对应 `configName` 已写 `generatedReportUrl`。

## 3. 故障现象对照

| 日志/现象 | 常见原因 | 处理 |
|-----------|----------|------|
| `user_access_token` / OAuth 重试 | Gateway `lark-mcp` 非 tenant | 改 `--token-mode tenant_access_token`，去掉 `--oauth`，重启 Gateway |
| Secret 截断 / token 过期 | 用户 token 或错误应用 | 核对 weekly-comparison `app-id`；强制 tenant |
| MCP 失败后 Legacy 出报告但内容旧 | fallback 读库/拉 Doc 用 tenant，数据本身陈旧 | 先修 Gateway；再查 SOURCE 飞书链与纪要 SQL |
| `docx_v1_document_rawContent` 不可用 | `-t` 未放行 wiki/docx | 合并白名单并 `mcp list` 确认工具名 |
| WS 超时 | `timeout-seconds` 过小 | Bot 设为 300+ |
| `failed to start server "meeting-mysql"` / `Connection closed` | npm 包名错误或 DB 连不上 | 改 `mcp-server-mysql`；env 用 `MYSQL_PASS`/`MYSQL_DB`；`mcp show meeting-mysql` 应有 `mysql_query`；核对 RDS 从 Gateway 主机可达 |
| `Unknown skill: matter-progress` | Skill 未部署到 JQClaw workspace | 复制 `skills/matter-progress/SKILL.md` → `/root/.openclaw/workspace/JQClaw/skills/matter-progress/`；`skills list` 确认后重启 Gateway |

## 4. Bot 侧（与 Gateway 配对）

| 键 | 生产建议 |
|----|----------|
| `feishu.weekly-comparison.openclaw.delegate-to-mcp` | `true` |
| `feishu.weekly-comparison.openclaw.legacy-fallback-on-mcp-failure` | `true`（Legacy 仅 **LLM + RestFeishuDocClient**） |
| `feishu.weekly-comparison.openclaw.timeout-seconds` | `300` |
| `MEETING_LLM_API_KEY` | 非 `test`（fallback 路径才需要有效 Key） |

Legacy **不再**使用第二条 OpenClaw WS（`OpenClawComparisonReportGenerator` 已移除）。

---

*清单版本：2026-05-24 · 与 matter-progress-core 复杂度简化方案 B/C 对齐*
