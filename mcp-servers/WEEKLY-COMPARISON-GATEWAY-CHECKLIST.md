# 会前对比 — OpenClaw Gateway 运维检查清单

> 对应 Bot 默认 `delegate-to-mcp=true`。配置参考：[openclaw-mcp-config.yml](openclaw-mcp-config.yml) · 工具表：[LARK-MCP-TOOLS.md](LARK-MCP-TOOLS.md) · 业务文档：[weekly-matter-comparison.md](../docs/weekly-matter-comparison.md)

## 1. 启动前（一次性 / 变更后）

- [ ] **安装 Skill `matter-progress`**：将 `smart-meeting-java/skills/matter-progress/` 部署到 JQClaw workspace：
  `mkdir -p /root/.openclaw/workspace/JQClaw/skills/matter-progress`
  并复制 `SKILL.md`（或 `openclaw skills install <path> --as matter-progress`）。
- [ ] **合并 MCP 配置**：将 `openclaw-mcp-config.yml` 中 `mcp.servers.lark-mcp` / `meeting-mysql` 写入生产 `openclaw.json` 的 **`mcp.servers`**（勿用顶层 `mcpServers`）。
- [ ] **`lark-mcp` token 模式**：`args` 含 `--token-mode` + **`tenant_access_token`**（写 Doc 用）。**禁止**对写 Doc 任务使用失效的用户 OAuth。
- [ ] **应用凭证**：`-a` / `-s` 与 Bot `feishu.weekly-comparison.app-id` / `app-secret` 为**同一** weekly-comparison 飞书应用（**写通报 Doc**）。
- [ ] **OpenAPI 白名单 `-t`**（v0.26 不再写飞书 Doc，**不需** docx 写权限；仅 meeting-mysql 查 oabp + 纪要）：
  ```
  # meeting-mysql MCP 即可，无 lark-mcp 必配项
  ```
- [ ] **`meeting-mysql` 包名**：`npx -y mcp-server-mysql`（**非** `@modelcontextprotocol/server-mysql`，后者 npm 404）。
- [ ] **`meeting-mysql` 环境变量**：`MYSQL_PASS` / `MYSQL_DB`（本包不用 `MYSQL_PASSWORD` / `MYSQL_DATABASE`）。
- [ ] **`meeting-mysql` 连通**：纪要查 `intelligence`；**SOURCE oabp SQL** 需 MCP MySQL 账号对 `oabp_pro`（或 `DB_OABP_NAME`）**SELECT**：
  ```sql
  GRANT SELECT ON oabp_pro.* TO 'meeting_mcp'@'%';
  -- 若 SQL JOIN system_users：
  GRANT SELECT ON oabp.system_users TO 'meeting_mcp'@'%';
  ```
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
- [ ] `mcp show lark-mcp` 显示 **`token_mode: tenant_access_token`**（或等价字段）。
- [ ] preset 各 SOURCE 会序已配置 **`oabpTaskSql`**（管理后台「会序与资料」）。
- [ ] 重启 Gateway 后，Bot 启动日志含：`weekly-comparison pipeline=OpenClaw-MCP` 与 `oabpSchema=oabp_pro`。
- [ ] 试跑：`POST /api/weekly-comparison/jobs/{id}/execute` → `last_run_status=SUCCESS`，preset `host_agenda` 对应 `configName` 已写 `generatedReportUrl`。

## 3. 故障现象对照

| 日志/现象 | 常见原因 | 处理 |
|-----------|----------|------|
| `SOURCE 未配置 oabpTaskSql` | 会序未配 SQL | 管理后台配置 oabpTaskSql；确认 source_config_names |
| MCP 读 oabp 失败 / Access denied | meeting-mysql 无 oabp SELECT | GRANT SELECT ON oabp_pro.* |
| MCP 失败后 Legacy 失败 | Bot 未启 oabp 直连 | `MEETING_DB_OABP_ENABLED=true` |
| `docx_v1_document_create` 不可用 | `-t` 未放行 docx | 合并白名单并 `mcp list` 确认 |
| WS 超时 | `timeout-seconds` 过小 | Bot 设为 300+ |
| `failed to start server "meeting-mysql"` | npm 包名或 DB 连不上 | 改 `mcp-server-mysql`；env 用 `MYSQL_PASS`/`MYSQL_DB` |
| run `FAILED` `Agent items 解析失败` | Agent 未输出 BEGIN/END_WEEKLY_COMPARISON_ITEMS 块 | 查 Skill 部署；查 Gateway 日志 |
| run `PARTIAL` `部分事项解析被丢弃` | statusLabel/category 不一致或必填缺失 | 查 run_error；调整 oabp SQL 列名 |
| `Unknown skill: matter-progress` | Skill 未部署 | 复制 `skills/matter-progress/SKILL.md` → JQClaw workspace |

## 4. Bot 侧（与 Gateway 配对）

| 键 | 生产建议 |
|----|----------|
| `feishu.weekly-comparison.openclaw.delegate-to-mcp` | `true` |
| `feishu.weekly-comparison.openclaw.legacy-fallback-on-mcp-failure` | `true` |
| `feishu.weekly-comparison.oabp.enabled` / `MEETING_DB_OABP_ENABLED` | `true`（Legacy fallback 需要） |
| `feishu.weekly-comparison.openclaw.timeout-seconds` | `300` |
| `MEETING_LLM_API_KEY` | 非 `test`（fallback 路径才需要有效 Key） |

---

*清单版本：2026-06-28 · SOURCE 改为 oabpTaskSql + meeting-mysql*
