# 智能会议文档索引（精简版）

文档更新时间：2026-06-25

当前核心文档：

| 文档 | 作用 |
|---|---|
| [USER-MANUAL.md](USER-MANUAL.md) | 当前版本功能说明、运行流程、常见问题排查 |
| [weekly-matter-comparison-USER-MANUAL.md](weekly-matter-comparison-USER-MANUAL.md) | 会前事项对比通报使用手册（v0.26 结果入库版） |
| [开发计划.md](开发计划.md) | **已完成**：前台多人、并发 P0/P1、Dashboard 白名单（`dashboard.user_grants`） |
| [版本迭代历史.md](版本迭代历史.md) | 版本时间线（0.01 起）+ **§7 发版规划至 2026-07-09**（每周 2 版） |
| [开关手册.md](开关手册.md) | 已实现开关、建议开关、运维配置组合 |
| [vc-minute-offline-transcribe.md](vc-minute-offline-transcribe.md) | **飞书 VC 妙记 → 讯飞离线全文转写**标准操作（脚本 SOP） |
| [feishu-vc-recording-design.md](feishu-vc-recording-design.md) | 飞书云录制 / File B 接入设计 |
| [config-ranges.md](config-ranges.md) | Admin/YAML 参数取值范围全量索引 |
| [assessments/并发能力评估与改造方案.md](assessments/并发能力评估与改造方案.md) | 10+ 并发容量评估、瓶颈分析与 P0/P1/P2 改造方案 |
| [repair-2026-06-24-full.md](repair-2026-06-24-full.md) | **安全/UX 审计 + 修复执行方案**（Part A/B/C，批次 B0–B4、测试与发版顺序） |
| [README.md](README.md) | 文档入口与维护约定（本文件） |

## 维护约定

- 功能行为变更：先更新 `USER-MANUAL.md`。
- 新增或调整配置开关：同步更新 `开关手册.md`。
- 并发/容量相关架构变更：同步更新 `assessments/并发能力评估与改造方案.md`。
- 跨模块开发里程碑：维护 `开发计划.md`，发版后同步 `版本迭代历史.md` §2/§7。
- 不再维护历史分散文档，避免多版本冲突。
