# 🔧 阿统团队 AI 编码规范 v1.0

> 基于《避免 AI 循环思考与 Token 浪费完整策略》提炼
> 适用范围：所有 Java/Spring Boot 项目及 AI Agent 协作开发
> 最后更新：2026-05-09

---

## 第一章：文件操作规范

### 1.1 创建前检查（幂等性）

**规则**：任何文件创建前，必须先检查文件是否存在。

```bash
# ✅ 正确做法
if [ -f "target.java" ]; then
    echo "SKIP: 文件已存在"
    exit 0
fi
```

```java
// ✅ 代码中同样适用
public void generateConfigFile(String path) {
    if (Files.exists(Paths.get(path))) {
        log.info("配置文件已存在，跳过生成: {}", path);
        return;
    }
    // 生成逻辑...
}
```

**违规后果**：重复创建同一文件消耗 token，触发 loopDetection。

### 1.2 修改前验证

**规则**：修改文件前必须验证内容是否已正确，避免无效修改。

```bash
# ✅ 正确做法
if grep -q "expected_pattern" target.java; then
    echo "SKIP: 内容已正确"
    exit 0
fi
```

### 1.3 修改后确认

**规则**：修改文件后必须验证修改是否生效。

```bash
# ✅ 检查哈希值变化
before_hash=$(md5sum target.java | cut -d' ' -f1)
# ... 执行修改 ...
after_hash=$(md5sum target.java | cut -d' ' -f1)
if [ "$before_hash" = "$after_hash" ]; then
    echo "WARNING: 修改未生效，停止重试"
    exit 1
fi
```

### 1.4 批量操作合并

**规则**：同类型的多个文件操作合并为一次工具调用。

```
❌ 错误: 分 3 次 edit 修改同一文件
✅ 正确: 1 次 edit 包含多个 edits 数组
✅ 正确: 1 次 exec 用 python/json 脚本批量修改
```

---

## 第二章：工具调用规范

### 2.1 工具调用次数限制

**规则**：同一工具连续调用上限为 **3 次**。

```
示例：
- exec 调用（相同命令）: 最多 2 次重试（共3次）
- web_search 调用（相同 query）: 最多 1 次重试（共2次）
- edit 调用（同一文件）: 合并为一次数组操作
```

**违规检测**：`tools.loopDetection.detectors.genericRepeat` 自动监控。

### 2.2 exec 调用规范

```bash
# ✅ 正确：一次执行多步，合并命令
mvn clean compile && mvn test -pl meeting-server

# ❌ 错误：分多次调用
mvn clean compile  # 第一次
mvn test           # 第二次（浪费一次工具调用）
```

### 2.3 process 轮询规范

**规则**：禁止循环 poll，必须使用 `timeout` 参数。

```javascript
// ✅ 正确：设置超时
process({ action: "poll", sessionId: "xxx", timeout: 30000 })

// ❌ 错误：无超时循环
while (true) { process({ action: "poll", sessionId: "xxx" }) }
```

**违规检测**：`tools.loopDetection.detectors.knownPollNoProgress` 自动阻塞。

### 2.4 结果变化检测

**规则**：工具调用返回结果与前一次完全相同且连续 **2 次**时，禁止第 3 次调用。

```
步骤1: exec("mvn compile") → 结果: BUILD FAILURE (error X)
步骤2: exec("mvn compile") → 结果: BUILD FAILURE (error X)  ← 阻止第3次
响应: "编译失败原因未变，已停止重试，需先修复 error X"
```

### 2.5 退避延迟

**规则**：重复调用必须加入指数退避。

| 重试次数 | 等待时间 |
|----------|----------|
| 第1次 | 立刻 |
| 第2次 | 等待 2s |
| 第3次 | 等待 5s |
| 超过3次 | 禁止，输出失败摘要 |

---

## 第三章：任务拆分规范

### 3.1 复杂任务必须拆分

**规则**：预计超过 **10 步**的任务，必须拆分为子任务。

```
✅ 正确拆分:
主任务: "实现用户认证模块"
├── 子任务1: 创建 JWT Token 工具类    [sessions_spawn]
├── 子任务2: 实现登录接口             [sessions_spawn]
├── 子任务3: 实现拦截器               [sessions_spawn]
└── 子任务4: 编写单元测试             [sessions_spawn]
```

### 3.2 subagent 使用模板

```javascript
// 标准 subagent 调用
sessions_spawn({
  task: "清晰描述单个任务目标",
  mode: "run",                 // 一次性任务
  timeoutSeconds: 120,         // 强制超时 2 分钟
  context: "isolated"          // 隔离上下文，避免累积
})
```

### 3.3 子任务超时强制

**规则**：每个子任务必须设置 `timeoutSeconds`，默认值：

| 任务类型 | timeoutSeconds |
|----------|---------------|
| 简单文件创建/修改 | 60 |
| 代码生成（少量） | 120 |
| 代码生成（大量）+ 测试 | 300 |
| 数据分析/搜索 | 180 |

### 3.4 子任务失败处理

**规则**：子任务失败不重试原 task，而是分析原因后重新 spawn。

```
❌ 错误: 在失败的子任务内循环重试
✅ 正确: 主会话接收失败报告 → 分析原因 → 修正 task 描述 → 重新 spawn
```

---

## 第四章：步数与终止规范

### 4.1 步数硬限制

**规则**：每个开发阶段的步数上限：

| 阶段 | 最大步数 |
|------|----------|
| 方案设计 | 8 步 |
| 代码实现 | 12 步 |
| 测试验证 | 8 步 |
| Bug 修复 | 5 步 |
| 单次对话总计 | 20 步 |

**超出处理**：输出进度摘要并请求用户确认是否继续。
```
"已达到本阶段步数上限 (12步)，当前进度: 80%
 已完成: A, B, C
 待完成: D
 是否继续？(回复 y 继续 / n 暂停)"
```

### 4.2 明确终止条件

**规则**：每个任务开始前必须声明完成标准。

```
📋 任务: 实现 MeetingService.java
✅ 完成条件:
  [ ] 文件存在: MeetingService.java
  [ ] 包含方法: createMeeting(), getMeeting(), updateStatus()
  [ ] 编译通过: mvn compile -pl meeting-server
  [ ] 单元测试: mvn test -Dtest=MeetingServiceTest (≥1 个测试)
```

### 4.3 终止触发条件（白名单）

**规则**：仅在以下情况可以终止任务：

```
✅ 允许终止:
1. 已成功完成所有完成条件
2. 已尝试至少 3 种不同方法且均失败
3. 用户明确要求停止
4. 达到步数上限

❌ 不允许:
- "可能还需要进一步优化" 触发的无限迭代
- 工具调用失败后的盲目重试
- 无新信息的重复搜索
```

---

## 第五章：代码质量与验证规范

### 5.1 编译验证前置

**规则**：所有代码修改后必须先编译验证，再继续下一步。

```bash
# 一阶段完成后的验证
mvn compile -pl meeting-server -q && echo "✅ 编译通过" || echo "❌ 编译失败"
```

### 5.2 单元测试覆盖率要求

| 模块类型 | 最低测试要求 |
|----------|-------------|
| Service 层 | 1 个核心方法测试 |
| Controller 层 | 1 个 API 端点测试 |
| 工具类 (Util) | 2 个核心方法测试 |
| 配置类 (Config) | 编译通过即可 |

### 5.3 自测完成确认

**规则**：每个模块开发完成后，开发者需执行自检清单：

```
□ 编译通过: mvn compile -pl meeting-server
□ 测试通过: mvn test -pl meeting-server
□ 无新增 warning: grep "WARN" 对比前后日志
□ 功能自测: 至少执行一次完整流程
```

---

## 第六章：Token 与资源预算规范

### 6.1 模型选择策略

**规则**：根据任务类型选择合适模型，避免"大模型做小任务"。

| 任务类型 | 模型 | 理由 |
|----------|------|------|
| 文件读写、简单搜索 | qwen3.6-plus | 响应快，token 消耗低 |
| 代码生成、代码分析 | DeepSeek-pro | 代码能力强 |
| 复杂架构设计、多步骤推理 | doubao-seed-2.0-pro | 推理深度高 |
| 子 agent 任务 | 继承主会话或使用默认 | 隔离上下文 |

### 6.2 thinking 级别控制

| 任务类型 | thinking | 说明 |
|----------|----------|------|
| 单步文件操作 | off | 无需深度推理 |
| 代码修改 | medium | 需要一定分析 |
| 架构设计 / 复杂 debug | high | 需要深度推理 |

### 6.3 Token 预算意识

**规则**：每个回复应控制在 **2000 token 以内**（约 500 中文字）。

- 代码块 > 100 行时使用文件承载，不嵌入对话
- 分析报告 > 500 字时写入文档，对话中只给摘要

---

## 第七章：日志与可观测性规范

### 7.1 操作日志记录

**规则**：所有文件创建/修改操作必须记录日志。

```bash
LOG_FILE="/tmp/agent-coding-$(date +%Y%m%d).log"
echo "$(date -Iseconds) | FILE_CREATE | /path/to/file.java | SUCCESS" >> "$LOG_FILE"
```

### 7.2 异常捕获与上报

**规则**：所有 try-catch 必须包含完整的错误上下文。

```java
// ✅ 正确
try {
    // 业务逻辑
} catch (Exception e) {
    log.error("操作失败: operation={}, target={}, error={}",
              "createFile", filePath, e.getMessage(), e);
    throw new ServiceException("文件创建失败", e);
}

// ❌ 错误
try {
    // 业务逻辑
} catch (Exception e) {
    log.error("失败");  // 无上下文
}
```

### 7.3 关键指标监控

| 指标 | 目标值 | 告警阈值 |
|------|--------|----------|
| 单次对话步数 | < 15 | > 20 |
| 编译失败次数 | 0 | > 2 |
| 同一文件修改次数 | < 2 | > 3 |
| 子任务成功率 | > 80% | < 50% |

---

## 第八章：团队协作规范

### 8.1 角色职责（固定分工）

| 角色 | 职责 | 负责人 |
|------|------|--------|
| 系统工程师 | 架构设计、核心模块、部署运维、二次验收 | 阿统 |
| 产品经理 | 需求梳理、迭代规划、体验优化、需求验收 | 阿品 |
| 全栈开发 | 业务功能、前端优化、三方对接、自测 | 阿栈 |

### 8.2 提测流程

```
阿栈自测通过 → 通知阿统 → 阿统二次验收 → 阿品需求验收 → 交付上线
```

### 8.3 代码审查检查点

```
□ 是否通过编译？
□ 是否通过单元测试？
□ 是否有循环调用风险？
□ 是否有重复文件操作？
□ 是否符合本编码规范？
```

---

## 第九章：循环检测与熔断规范

### 9.1 系统级防护（已配置）

```json
{
  "loopDetection": {
    "enabled": true,
    "warningThreshold": 3,
    "criticalThreshold": 8,
    "globalCircuitBreakerThreshold": 15,
    "detectors": {
      "genericRepeat": true,
      "knownPollNoProgress": true,
      "pingPong": true
    }
  }
}
```

### 9.2 开发者自检清单

每次工具调用前自问：

```
□ 这个工具调用是否必要？（非必要则跳过）
□ 我之前是否已经调用过？（是 → 检查结果是否已满足需求）
□ 调用失败后我最多重试几次？（设置上限 3 次）
□ 调用结果不同我该如何处理？（预案）
```

### 9.3 熔断后的兜底方案

```
1. 停止当前操作序列
2. 输出已完成的工作摘要
3. 明确列出未完成的部分
4. 请求用户决策下一步
```

---

## 附录 A：禁止事项清单

| 禁止行为 | 替代方案 |
|----------|----------|
| ❌ 循环 poll 进程状态 | ✅ 使用 process(timeout=N) |
| ❌ 分多次修改同一文件 | ✅ 合并为一次 edit 多 edits |
| ❌ 盲目重试失败的编译 | ✅ 分析错误日志后再试 |
| ❌ 无终止条件的迭代优化 | ✅ 明确完成标准后才开始 |
| ❌ 在大任务中反复调整 | ✅ 拆分子任务，独立完成 |
| ❌ thinking=high 做简单查询 | ✅ thinking=off 或 medium |
| ❌ 在对话中嵌 >100 行代码 | ✅ 写入文件后引用路径 |

## 附录 B：快速检查命令

```bash
# 编译验证
mvn compile -pl meeting-server -q

# 测试验证
mvn test -pl meeting-server

# 检查最近操作日志
tail -20 /tmp/agent-coding-$(date +%Y%m%d).log

# 检查同一文件修改次数
grep "target.java" /tmp/agent-coding-*.log | wc -l

# 检查构建状态
openclaw status
```
