# weekly-comparison MCP 写 Doc — 已落地

代码与配置已合并；生产 Gateway 仍需手工同步 `openclaw.json` 的 `-t` 白名单（见 USER-MANUAL §12）。

## 已改文件

- `OpenClawMcpWeeklyComparisonDelegate.java` — 工具名 + 执行手册（classpath 模板）
- `weekly-comparison-mcp-instructions.template` — 中文执行手册 UTF-8
- `openclaw-mcp-config.yml` — docx/wiki `-t`
- `skills/matter-progress/SKILL.md` — allowed-tools + weekly-comparison-mcp 节
- `mcp-servers/LARK-MCP-TOOLS.md`
- `feishu-scheduled-bot` `application.yml` / `application-prod.yml` — timeout 300、legacy-fallback true
- `feishu-scheduled-bot/docs/USER-MANUAL.md` §12

## 生产部署

1. 合并 `openclaw-mcp-config.yml` 的 `-t` 到 Gateway `openclaw.json`，重启 Gateway
2. `openclaw --profile clone-boss mcp list` 确认 docx/wiki 工具
3. 重启 Bot（或设 `FEISHU_WEEKLY_COMPARISON_OPENCLAW_TIMEOUT=300`）
4. `POST /api/weekly-comparison/jobs/{id}/execute` 验收
