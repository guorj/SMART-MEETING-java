# 循环检测与防护机制

## 问题诊断

### 观察到的循环模式
从工作记录中发现以下重复模式：
- `LocalEventBus created` 重复出现 3 次
- `OfflineCorrectionService created` 重复出现 3 次
- 同一文件在短时间内被多次修改

### 根本原因分析

1. **缺乏操作去重机制**
   - 每次执行任务时未检查是否已创建/修改过目标文件
   - 没有记录最近的操作历史

2. **重试逻辑缺失**
   - 失败后无限制重试，而非设置最大重试次数
   - 未实现指数退避策略

3. **状态追踪不足**
   - 未跟踪文件修改时间戳
   - 未验证修改是否真正生效

## 防护机制

### 1. 操作去重检查

```bash
# 创建文件前检查是否存在且内容正确
if [ -f "target.java" ]; then
    if grep -q "expected_content" target.java; then
        echo "✅ File already exists with correct content"
        exit 0
    fi
fi
```

### 2. 最大重试次数限制

```python
MAX_RETRIES = 3
retry_count = 0

while retry_count < MAX_RETRIES:
    try:
        result = execute_operation()
        if result.success:
            break
    except Exception as e:
        retry_count += 1
        if retry_count >= MAX_RETRIES:
            log.error(f"Max retries ({MAX_RETRIES}) exceeded")
            raise FallbackError("Operation failed after max retries")
        time.sleep(2 ** retry_count)  # 指数退避
```

### 3. 操作日志追踪

```bash
# 记录操作历史
echo "$(date '+%Y-%m-%d %H:%M:%S') | $OPERATION | $FILE | $STATUS" >> /tmp/operation-log.txt

# 检查最近5分钟内的重复操作
tail -20 /tmp/operation-log.txt | grep "$OPERATION" | grep "$(date '+%Y-%m-%d %H:%M')"
```

### 4. 兜底方案

当检测到循环或超过最大重试次数时：

1. **立即停止当前操作**
2. **记录失败原因和上下文**
3. **切换到降级方案**：
   - 使用已知的稳定版本文件
   - 跳过非关键步骤
   - 输出明确的错误报告

## 实施检查清单

- [ ] 每次文件操作前检查是否已存在
- [ ] 设置 MAX_RETRIES=3
- [ ] 实现指数退避重试
- [ ] 记录操作日志
- [ ] 准备兜底方案
- [ ] 定期检查操作日志中的重复模式
