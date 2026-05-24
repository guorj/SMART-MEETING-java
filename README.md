# 智能会议系统（smart-meeting-java）

以飞书为入口的会议闭环后端：Spring Boot 单体服务 + 内嵌静态录音/主持页，对接讯飞 ASR/TTS、飞书 Open API 与 LLM 纪要/待办。

## 快速启动

```bash
cp .env.example .env   # 填写 DB、飞书、讯飞等配置
docker compose up -d   # MySQL / Redis / Kafka + meeting-server
```

本地开发（需本机 MySQL/Redis/Kafka 或改 `application-dev.yml`）：

```bash
./mvnw -pl meeting-server spring-boot:run
```

默认 HTTP 端口 **8765**；健康检查 `GET /api/v1/health`。

## 目录结构

```
smart-meeting-java/
├── meeting-server/          # 唯一应用模块（Java + static 前端）
├── matter-progress-core/    # 会前对比核心库（供 feishu-scheduled-bot 依赖）
├── sql/                     # 共库全量 DDL + 索引，见 sql/README.md
├── meeting-server/src/main/resources/
│   ├── schema.sql           # meeting 11 表（新环境维护入口）
│   ├── schema-data.sql      # 种子 DML
│   ├── schema-upgrade/      # 已有库增量 v0.4–v0.10
│   ├── schema-seed/         # 运维幂等种子
│   └── schema-examples/     # 参考 INSERT，不自动执行
├── deploy/nginx/            # 生产 HTTPS 反代示例（非 compose 内置）
├── docs/                    # 文档索引见 docs/README.md
├── docker-compose.yml
├── pom.xml                  # Maven 聚合父 POM
└── .env.example
```

## 文档

| 类型 | 路径 |
|------|------|
| **用户手册** | [docs/USER-MANUAL.md](docs/USER-MANUAL.md) |
| **会前事项对比通报** | [docs/weekly-matter-comparison.md](docs/weekly-matter-comparison.md) |
| 二期 PRD | [docs/PRD-Java-二期.md](docs/PRD-Java-二期.md) |
| 编码规范 | [docs/coding-standards.md](docs/coding-standards.md) |
| 库表分析 | [docs/数据库表结构冗余与字段合理性分析.md](docs/数据库表结构冗余与字段合理性分析.md) |
| 产品/形态/主持 | [docs/product/](docs/product/) |
| 评估报告归档 | [docs/assessments/](docs/assessments/)（含 **10+ 并发生产可行性**） |
| 汇报 PPT/PDF | [docs/assets/](docs/assets/) |

## 构建

```bash
./mvnw clean package -pl meeting-server
```
