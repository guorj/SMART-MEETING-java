# MCP Tool 名称参考（lark-mcp + meeting-mysql）

本文档列出当前 Gateway 配置下 **Agent / Skill 可用的 MCP 工具全名**，以及 OpenAPI 与白名单的对应关系。

**会前对比 Gateway 部署**：见 [WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md](WEEKLY-COMPARISON-GATEWAY-CHECKLIST.md)（`tenant_access_token`、白名单、启动后 `mcp show` 验证）。

## 命名规则

| 层级 | 格式 | 示例 |
|------|------|------|
| 飞书 OpenAPI（`-t` 白名单） | `{域}.v{版本}.{资源}.{动作}` | `bitable.v1.appTableRecord.search` |
| lark-mcp 注册名（`-c snake`，默认） | 将 `.` 替换为 `_`，保留段内 camelCase | `bitable_v1_appTableRecord_search` |
| OpenClaw Agent 调用名 | `{mcp.servers 键名}__{lark-mcp 注册名}` | `lark-mcp__bitable_v1_appTableRecord_search` |

核对命令：

```bash
openclaw --profile clone-boss mcp list
openclaw --profile clone-boss mcp show lark-mcp
openclaw --profile clone-boss mcp show meeting-mysql
```

若 `-c` 改为 `camel` / `dot` / `kebab`，OpenClaw 工具名会变化，须同步改 Skill 的 `allowed-tools`。

---

## lark-mcp（白名单：读表 + 读/写 Doc + 读 wiki）

配置见 `openclaw-mcp-config.yml` → `mcp.servers.lark-mcp`；`-t` 启用下列 API。

| # | OpenAPI（`-t`） | lark-mcp 注册名（snake） | OpenClaw 全名 | 用途 | 使用 Skill |
|---|------------------|-------------------------|---------------|------|------------|
| 1 | `bitable.v1.appTableField.list` | `bitable_v1_appTableField_list` | `lark-mcp__bitable_v1_appTableField_list` | 获取数据表字段列表 | `progress-analysis`、`matter-progress` |
| 2 | `bitable.v1.appTableRecord.search` | `bitable_v1_appTableRecord_search` | `lark-mcp__bitable_v1_appTableRecord_search` | 分页读取多维表记录 | `progress-analysis`、`matter-progress` |
| 3 | `docx.v1.document.create` | `docx_v1_document_create` | `lark-mcp__docx_v1_document_create` | 创建 docx 云文档 | `matter-progress`（weekly-comparison-mcp） |
| 4 | `docx.v1.documentBlockChildren.create` | `docx_v1_documentBlockChildren_create` | `lark-mcp__docx_v1_documentBlockChildren_create` | 向根 block 批量追加正文 | `matter-progress`（weekly-comparison-mcp） |
| 5 | `docx.v1.document.rawContent` | `docx_v1_document_rawContent` | `lark-mcp__docx_v1_document_rawContent` | 读 docx 纯文本 | `matter-progress`（weekly-comparison-mcp） |
| 6 | `wiki.v2.space.getNode` | `wiki_v2_space_getNode` | `lark-mcp__wiki_v2_space_getNode` | 解析 wiki 节点 | `matter-progress`（weekly-comparison-mcp） |

### 写 Doc 要点（weekly-comparison）

- 创建后 **`block_id` = `document_id`**（根 block）
- `documentBlockChildren.create` **每批 ≤50** 个 text block（`block_type=2`）
- 不存在 `document_block_children_batch_create`；正确工具名为 **`documentBlockChildren_create`**

### 调用要点（Agent）

**`lark-mcp__bitable_v1_appTableField_list`**

- **路径参数**：`app_token`（多维表 app_token）、`table_id`（数据表 ID）
- **说明**：先调此工具了解列结构，再读记录

**`lark-mcp__bitable_v1_appTableRecord_search`**

- **路径参数**：`app_token`、`table_id`
- **查询/体**：`page_size` 建议 100（最大 500）；分页用返回的 `page_token`
- **说明**：读完「📋综合管理事项代办清单」相关记录；筛选条件按字段名构造 filter（值为数组）

### 认证与权限

- `--token-mode tenant_access_token`：以应用机器人身份读表（与 `channels.feishu` 同一应用）
- 飞书权限：`bitable:app:readonly` 或 `bitable:app` + `base:table:read` 等

### 扩展读 API（未启用，仅供参考）

在 `-t` 中追加 OpenAPI 名后，按 snake 规则得到 OpenClaw 名，例如：

| OpenAPI | OpenClaw 全名（snake） | 说明 |
|---------|------------------------|------|
| `bitable.v1.appTable.list` | `lark-mcp__bitable_v1_appTable_list` | 列出数据表 |
| `docx.v1.document.rawContent` | `lark-mcp__docx_v1_document_rawContent` | 读文档纯文本 |
| `docx.builtin.search` | `lark-mcp__docx_builtin_search` | 搜索文档 |
| `im.v1.message.list` | `lark-mcp__im_v1_message_list` | 消息列表（写接口勿混开） |

完整列表（**1271 个 tool / 61 业务域**）：[LARK-MCP-TOOLS-FULL.md](./LARK-MCP-TOOLS-FULL.md)（由官方 [tools-en.md](https://github.com/larksuite/lark-openapi-mcp/blob/main/docs/reference/tool-presets/tools-en.md) 生成）。

预设集合：[presets.md](https://github.com/larksuite/lark-openapi-mcp/blob/main/docs/reference/tool-presets/presets.md)。

---

## meeting-mysql（官方 @modelcontextprotocol/server-mysql）

配置见 `mcp.servers.meeting-mysql`；生产建议使用 **只读** MySQL 账号，并在 Skill 中禁止 `insert` / `update`。

| # | MCP 工具名 | OpenClaw 全名 | 用途 | 使用 Skill |
|---|------------|---------------|------|------------|
| 1 | `query` | `meeting-mysql__query` | 执行 SQL（会后分析常用） | `progress-analysis`、`minute-enhancement` |
| 2 | `list_tables` | `meeting-mysql__list_tables` | 列出库表 | 一般不由 Skill 直接调用 |
| 3 | `describe_table` | `meeting-mysql__describe_table` | 查看表结构 | 一般不由 Skill 直接调用 |
| 4 | `insert` | `meeting-mysql__insert` | 插入行（写） | **未列入 allowed-tools** |
| 5 | `update` | `meeting-mysql__update` | 更新行（写） | **未列入 allowed-tools** |

### `meeting-mysql__query` 示例（progress-analysis）

```sql
SELECT status, COUNT(*) as cnt
FROM int_meeting_todo
WHERE meeting_id = '{previousMeetingId}'
GROUP BY status
```

---

## Skill allowed-tools 汇总

| Skill | allowed-tools |
|-------|----------------|
| `progress-analysis` | `lark-mcp__bitable_v1_appTableField_list`、`lark-mcp__bitable_v1_appTableRecord_search`、`meeting-mysql__query` |
| `matter-progress` | bitable 读 + mysql query；weekly-comparison-mcp 另需 `docx_v1_document_create`、`docx_v1_documentBlockChildren_create`、`docx_v1_document_rawContent`、`wiki_v2_space_getNode` |
| `minute-enhancement` | `meeting-mysql__query` |

---

## tool-name-case 对照（当前白名单 2 个 API）

| OpenAPI | snake（当前 `-c snake`） | camel | dot | kebab |
|---------|--------------------------|-------|-----|-------|
| `bitable.v1.appTableField.list` | `bitable_v1_appTableField_list` | `bitableV1AppTableFieldList` | `bitable.v1.appTableField.list` | `bitable-v1-appTableField-list` |
| `bitable.v1.appTableRecord.search` | `bitable_v1_appTableRecord_search` | `bitableV1AppTableRecordSearch` | `bitable.v1.appTableRecord.search` | `bitable-v1-appTableRecord-search` |

OpenClaw 前缀均为 `lark-mcp__`，例如 `lark-mcp__bitable_v1_appTableField_list`。