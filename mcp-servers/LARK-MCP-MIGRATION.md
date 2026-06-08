# lark-mcp 迁移说明（2026-05-21）

## 状态：已完成

| 项 | 状态 |
|----|------|
| `mcp-servers/openclaw-mcp-config.yml` | `mcp.servers` + 注释 |
| `mcp-servers/LARK-MCP-TOOLS.md` | **Tool 全名与 OpenAPI 对照表** |
| `instances/clone-boss/config/openclaw.json` | `mcp.servers` + `plugins.bundledDiscovery` |
| `skills/*` | `allowed-tools` 已对齐 |
| `mcp-servers/feishu-bitable/` | 已删除 |

## Tool 名称（速查）

详见 [LARK-MCP-TOOLS.md](./LARK-MCP-TOOLS.md)。

| OpenClaw 全名 | 说明 |
|---------------|------|
| `lark-mcp__bitable_v1_appTableField_list` | 多维表字段列表 |
| `lark-mcp__bitable_v1_appTableRecord_search` | 多维表记录检索 |
| `meeting-mysql__mysql_query` | SQL 查询（`mcp-server-mysql`；会后/纪要 Skill） |

## 部署与验证

配置须为 **`mcp.servers`**（不是 `mcpServers`），并建议 `plugins.bundledDiscovery: "compat"`。

运行时配置路径（clone-boss）：`~/.openclaw-clone-boss/openclaw.json`（与 `instances/clone-boss/config/openclaw.json` 需保持一致）。

```bash
openclaw --profile clone-boss doctor --fix
openclaw --profile clone-boss config validate
openclaw --profile clone-boss mcp list
openclaw --profile clone-boss gateway restart
```

## 生产环境

- `mcp.servers.meeting-mysql.env` 使用生产库（**勿**只改 Bot 的 `application-prod.yml`）。
- 推荐 **字面量** `MYSQL_HOST: "60.205.1.17"`，见 `openclaw-mcp-config.yml`。
- 改配置后必须 `openclaw gateway restart`；否则旧 MCP 会话仍连 localhost（缓存最长约 10 分钟）。
- 飞书凭证与 `channels.feishu.accounts.boss_clone` 一致。

### 仍显示 localhost:3306 时排查

1. `openclaw --profile <你的profile> mcp show meeting-mysql` — 看 **env 里 MYSQL_HOST 实际值**。
2. 确认改的是 **Gateway 正在用的** `~/.openclaw-<profile>/openclaw.json`，不是仓库里的 yml 模板。
3. `${DB_HOST}` 未导出 → OpenClaw 加载配置失败，或你看到的是 Agent 臆测文案而非 MCP 真连库。
4. Bot（8764）与 OpenClaw Gateway（18789）是 **两个进程**，各用各的数据源配置。

## 白名单扩展

在 `lark-mcp` 的 `-t` 追加 OpenAPI 名 → 查 [LARK-MCP-TOOLS.md](./LARK-MCP-TOOLS.md) 算 snake 名 → 更新 Skill `allowed-tools`。

## 回滚

恢复自研 `feishu-bitable` 与 `feishu-bitable__read_bitable_rows` / `feishu-bitable__list_bitable_fields`。
