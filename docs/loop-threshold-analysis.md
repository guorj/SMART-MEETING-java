# loopDetection 阈值合理性分析

## 一、当前配置

```json
{
  "historySize": 30,
  "warningThreshold": 10,
  "criticalThreshold": 20,
  "globalCircuitBreakerThreshold": 30
}
```

## 二、数值关系验证

| 检查项 | 结果 | 说明 |
|--------|------|------|
| warning < critical | ✅ 10 < 20 | 符合官方文档要求 |
| critical < globalCircuitBreaker | ✅ 20 < 30 | 符合官方文档要求 |
| globalCircuitBreaker ≤ historySize | ✅ 30 ≤ 30 | 熔断阈值不超过分析窗口 |

**结论**: 数值关系合规。

## 三、阈值含义与场景匹配分析

### 3.1 historySize = 30（合理）

**作用**: 保留最近30次工具调用用于循环分析

**评估**:
- ✅ 覆盖足够的操作历史，不会漏检长循环
- ✅ 内存开销可控（约几KB）
- ✅ 适合复杂任务的工具调用序列

### 3.2 warningThreshold = 10（偏宽松）

**作用**: 10次重复无进展 → 发出警告

**问题匹配分析**:
| 场景 | 重复次数 | 是否触发警告 |
|------|----------|-------------|
| 用户之前遇到的问题 | 3次 | ❌ 不触发 |
| 中度循环问题 | 5-8次 | ❌ 不触发 |
| 严重循环问题 | 10+次 | ✅ 触发 |

**评估**:
- ⚠️ **偏宽松**: 用户之前只重复了3次就消耗大量token
- ⚠️ warningThreshold=10 需要10次才警告，无法及时捕捉早期循环
- ⚠️ 对于token消耗敏感的场景，应该更严格

### 3.3 criticalThreshold = 20（合理）

**作用**: 20次重复 → 阻塞关键循环

**评估**:
- ✅ 给warning后有10次缓冲空间（10→20）
- ✅ 在循环升级前进行干预
- ✅ 合理的干预时机

### 3.4 globalCircuitBreakerThreshold = 30（合理）

**作用**: 30次 → 硬停止（熔断）

**评估**:
- ✅ 最终兜底机制
- ✅ 与historySize一致，分析窗口满就熔断
- ✅ 确保不会无限循环

## 四、与用户实际问题的匹配度

### 用户遇到的问题特征

```
时间: ~01:00 AM
现象: 反复创建/修改文件
重复次数: 3次
结果: 消耗大量token，用户手动中断
```

### 当前阈值是否能捕捉？

| 阈值 | 能否捕捉3次重复？ | 建议值 |
|------|------------------|--------|
| warningThreshold=10 | ❌ 不能 | 建议降至 **3-5** |
| criticalThreshold=20 | ❌ 不能 | 建议降至 **8-10** |
| globalCircuitBreakerThreshold=30 | ❌ 不能 | 建议降至 **15-20** |

## 五、阈值调整建议

### 方案A：激进防护（推荐）

```json
{
  "historySize": 30,
  "warningThreshold": 3,
  "criticalThreshold": 8,
  "globalCircuitBreakerThreshold": 15
}
```

**适用场景**:
- Token消耗敏感
- 开发调试阶段
- 已发生过循环问题

**特点**:
- 3次重复立即警告（匹配用户之前的问题）
- 8次干预，给5次缓冲空间
- 15次熔断，确保不会超过半数窗口

### 方案B：平衡防护

```json
{
  "historySize": 30,
  "warningThreshold": 5,
  "criticalThreshold": 10,
  "globalCircuitBreakerThreshold": 20
}
```

**适用场景**:
- 生产环境
- 需要容忍少量重试
- 工具调用序列复杂

**特点**:
- 5次警告，比当前严格50%
- 10次干预，给5次缓冲
- 20次熔断，保持足够安全边际

### 方案C：宽松防护（当前配置）

```json
{
  "historySize": 30,
  "warningThreshold": 10,
  "criticalThreshold": 20,
  "globalCircuitBreakerThreshold": 30
}
```

**适用场景**:
- 工具调用频繁但有效
- 需要大量重试的任务
- 对循环容忍度高

**风险**:
- ⚠️ 无法及时捕捉早期循环
- ⚠️ 可能在警告前已消耗大量资源

## 六、推荐结论

基于用户之前遇到的循环问题（3次重复消耗大量token），**强烈推荐采用方案A（激进防护）**。

### 理由

1. **匹配实际问题**: warningThreshold=3 能捕捉用户之前遇到的循环
2. **及时干预**: 在循环早期警告，避免资源浪费
3. **安全边际**: 各阈值间保持合理缓冲（3→8→15）
4. **合规**: 满足官方文档的严格递增要求

### 调整命令

```bash
# 更新配置
openclaw config set tools.loopDetection.warningThreshold 3
openclaw config set tools.loopDetection.criticalThreshold 8
openclaw config set tools.loopDetection.globalCircuitBreakerThreshold 15

# 重启生效
openclaw gateway restart
```