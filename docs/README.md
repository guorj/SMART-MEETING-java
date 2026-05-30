# 文档索引

## 用户手册

| 文档 | 说明 |
|------|------|
| [**USER-MANUAL.md**](USER-MANUAL.md) | **完整用户手册**（三场景音频 + Pipeline 二期编排 + 双服务协作） |
| [**weekly-matter-comparison.md**](weekly-matter-comparison.md) | **会前事项对比通报**（v0.9：bot 定时对比、config_role、Job 参数、故障排查） |
| [**langfuse-integration-plan.md**](langfuse-integration-plan.md) | **Langfuse Prompt 管理集成方案**（项目结构图、分期落地、PromptRegistry 设计） |
| [**meeting-admin.md**](meeting-admin.md) | **会议管理后台**（`meeting-admin-server` :8766，与 meeting-server 分离启动） |
| [**会前流程编排指南-零基础.md**](会前流程编排指南-零基础.md) | **零基础会务可用**：后台一步步配置会前 24h / 10min 工作流 |
| [**meeting-admin.md#二期流水线事件驱动升级v017**](meeting-admin.md#二期流水线事件驱动升级v017) | 二期落地：状态机 + Outbox + Pipeline DDL/API/UI |
| [**meeting-admin.md#三场景音频链路升级v018**](meeting-admin.md#三场景音频链路升级v018) | 三场景（线下/混合/线上）场景字段 + 云端录音兜底 |
| [`scripts/start-dev.sh`](../scripts/start-dev.sh) | 开发一键启动 server / admin / both + 依赖检查 |
| [admin-extensibility.md](admin-extensibility.md) | 后台 L1/L2/L3 扩展点开发说明 |
| [meeting-admin-p2.md](meeting-admin-p2.md) | 后台 P2：internal API、OAuth、反代 |
| [product/混合参会检点-用户手册.md](product/混合参会检点-用户手册.md) | 混合检点专题（与 USER-MANUAL 第 9 章同步） |

## 权威 / 日常维护

| 文档 | 说明 |
|------|------|
| [PRD-Java-二期.md](PRD-Java-二期.md) | 二期需求与架构主文档（**v2.2：§1.5 六阶段能力图谱 + 实现进度**） |
| [coding-standards.md](coding-standards.md) | Java/前端编码约定 |
| [数据库表结构冗余与字段合理性分析.md](数据库表结构冗余与字段合理性分析.md) | DDL 与生产库结构分析 |

## product/ — 产品形态与 AI 主持

| 文档 | 说明 |
|------|------|
| [智能会议系统-项目最终形态.md](product/智能会议系统-项目最终形态.md) | 目标态用户旅程（愿景） |
| [智能会议系统-项目最终形态-展示版.md](product/智能会议系统-项目最终形态-展示版.md) | 同上，带阶段进度标注（汇报用） |
| [AI会议主持人PRD_v1.md](product/AI会议主持人PRD_v1.md) | AI 主持人一期 PRD |
| [AI会议主持人-开发文档.md](product/AI会议主持人-开发文档.md) | 主持能力技术说明 |
| [混合参会检点-用户手册.md](product/混合参会检点-用户手册.md) | 混合检点专题（完整流程见 [USER-MANUAL.md](USER-MANUAL.md)） |
| [混合参会-音频与检点策略.md](product/混合参会-音频与检点策略.md) | 混合会场音频与检点技术策略 |
| [细化文档.md](product/细化文档.md) | 产品备忘要点 |

## assessments/ — 阶段性评估（归档）

| 文档 | 说明 |
|------|------|
| [会中模块进度评估.md](会中模块进度评估.md) | **会中 10 项**矩阵 + v0.9 会前对比迁移状态（2026-05-23） |
| [meeting-server schema-upgrade v0.17](../meeting-server/src/main/resources/schema-upgrade/v0.17-pipeline-outbox-statemachine.sql) | 二期 Phase1-5 结构升级 SQL（Outbox / Pipeline / 幂等） |
| [meeting-server schema-upgrade v0.18](../meeting-server/src/main/resources/schema-upgrade/v0.18-meeting-scenario-audio-fallback.sql) | 二期 Phase6 场景字段与云端音频兜底 SQL |
| [meeting-server schema-upgrade v0.19](../meeting-server/src/main/resources/schema-upgrade/v0.19-normalize-feishu-userid.sql) | user_id 主键化数据归一化（open_id → user_id，含备份） |
| [meeting-server schema-upgrade v0.19 rollback](../meeting-server/src/main/resources/schema-upgrade/v0.19-rollback-normalize-feishu-userid.sql) | v0.19 回滚脚本（按备份表恢复） |
| [meeting-server schema-upgrade v0.20](../meeting-server/src/main/resources/schema-upgrade/v0.20-drop-feishu-open-union-id.sql) | 删除映射表 feishu_open_id / feishu_union_id（user_id 硬切） |
| [MCP+Skill改造方案.md](MCP+Skill改造方案.md) | MCP/Skill 改造；matter-progress 已改为 bot/LLM 路径 |
| [matter-progress-core/README.md](../matter-progress-core/README.md) | 会前对比核心库类说明 |
| [feishu-scheduled-bot 文档](../../feishu-scheduled-bot/docs/README.md) | bot 侧 USER-MANUAL、PRD、M9 计划 |
| [会中核心类-JavaDoc参考.md](会中核心类-JavaDoc参考.md) | 会中核心 Java 类/方法 JavaDoc 全文（待写入源码） |
| [智能会议系统-评估报告.md](assessments/智能会议系统-评估报告.md) | 功能完成度与里程碑 |
| [智能会议系统-技术可行性评估.md](assessments/智能会议系统-技术可行性评估.md) | 技术风险与缺陷清单 |
| [智能会议系统-全流程闭环评估报告.md](assessments/智能会议系统-全流程闭环评估报告.md) | 端到端闭环评估 |
| [智能会议系统-生产环境与10+并发技术可行性评估.md](assessments/智能会议系统-生产环境与10+并发技术可行性评估.md) | **生产上线与 10+ 并发容量**（2026-05-15） |

## assets/ — 对外材料

- [智能会议系统-项目汇报PPT_v3.pptx](assets/智能会议系统-项目汇报PPT_v3.pptx)
- [智能会议系统-项目最终形态-展示版.pdf](assets/智能会议系统-项目最终形态-展示版.pdf)

## ref/ — 第三方 SDK 参考源码

讯飞 RTASR、声纹识别官方 Java Demo 的**只读参考**，不参与 Maven 构建。详见 [ref/README.md](ref/README.md)。
