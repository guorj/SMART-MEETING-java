# matter-progress-core

飞书资料导出与 OpenClaw Gateway 客户端的 **共享 Java 库**（`com.smartmeeting:matter-progress-core:0.1.0`）。

- **不是** Spring Boot 应用
- **`meeting-server`** 依赖本 jar 的 `feishu/*`（Bitable/Docx/Sheet 导出）与 `openclaw/*`（Gateway WS 客户端）
- 周事项校对（weekly matter comparison）功能已移除

## 主要包

| 包 | 职责 |
|----|------|
| `feishu/` | Bitable/Docx/Sheet 结构化导出、纯文本拉取 |
| `openclaw/` | OpenClaw Gateway WebSocket 客户端、回复提取、任务 ID |
| `config/` | `SpreadsheetFetchLimits` 等共享配置模型 |
