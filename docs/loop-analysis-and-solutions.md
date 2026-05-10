# 循环问题完整分析与解决方案

## 一、问题现象

用户反馈：在之前的开发工作中陷入了循环，消耗大量token。

## 二、问题诊断

### 2.1 AI行为层面的循环模式

从工作记录中观察到的重复模式：

| 时间戳 | 操作 | 重复次数 |
|--------|------|----------|
| 23:22-00:13 | LocalEventBus 创建/修改 | 3次 |
| 23:22-00:19 | OfflineCorrectionService 创建/修改 | 3次 |
| 00:43-01:08 | MinuteGenerationService 修改 | 多次 |

**根因分析**：
1. 缺乏操作幂等性检查 - 每次执行都重新创建/修改文件
2. 未设置重试上限 - 失败后无限重试
3. 状态验证缺失 - 未确认修改是否真正生效就重复操作

### 2.2 代码层面循环依赖检查

```
服务依赖关系图：
RecordingService → LocalEventBus → MinuteGenerationService
                                      ↓
                        OfflineCorrectionService ✓ (无循环)
                                      ↓
                           VoiceprintService ✓ (无循环)
                                      ↓
                              FeishuService ✓ (无循环)

AsrBridgeService → AudioCacheService ✓ (无循环)
```

**结论**：代码层面无循环依赖，问题出在AI agent的行为模式。

## 三、解决方案

### 3.1 立即实施的防护措施

#### 方案A：操作前检查脚本

```bash
#!/bin/bash
# check-before-write.sh - 文件操作前检查

TARGET_FILE="$1"
EXPECTED_CONTENT="$2"

if [ -f "$TARGET_FILE" ]; then
    if grep -q "$EXPECTED_CONTENT" "$TARGET_FILE"; then
        echo "SKIP: File already contains expected content"
        echo "File: $TARGET_FILE"
        echo "Last modified: $(stat -c '%y' $TARGET_FILE)"
        exit 0
    else
        echo "UPDATE: File exists but content differs"
    fi
else
    echo "CREATE: File does not exist"
fi
```

#### 方案B：操作日志与循环检测

```bash
#!/bin/bash
# operation-tracker.sh - 操作追踪与循环检测

LOG_FILE="/tmp/agent-operations.log"
WINDOW_MINUTES=5
MAX_SAME_OPS=2

log_operation() {
    local op="$1"
    local target="$2"
    local status="$3"
    
    echo "$(date '+%Y-%m-%d %H:%M:%S') | $op | $target | $status" >> "$LOG_FILE"
    
    # 检查最近窗口内的重复操作
    local count=$(tail -50 "$LOG_FILE" | \
        grep "$op" | \
        grep "$target" | \
        awk -F'|' '{print $1}' | \
        while read ts; do
            if [ $(date -d "$ts" +%s) -gt $(($(date +%s) - ${WINDOW_MINUTES}*60)) ]; then
                echo "1"
            fi
        done | wc -l)
    
    if [ "$count" -gt "$MAX_SAME_OPS" ]; then
        echo "WARNING: Loop detected! Operation '$op' on '$target' repeated $count times in ${WINDOW_MINUTES}min"
        exit 1
    fi
}
```

### 3.2 长期改进建议

#### 建议1：实现操作幂等性
- 所有文件操作前检查目标状态
- 使用 `diff` 验证是否需要修改
- 记录操作前后的文件哈希值

#### 建议2：设置全局重试限制
```python
# 全局配置
MAX_RETRIES_PER_OPERATION = 3
GLOBAL_RETRY_WINDOW = 300  # 5分钟窗口

def execute_with_retry(operation, *args, **kwargs):
    retries = 0
    while retries < MAX_RETRIES_PER_OPERATION:
        try:
            return operation(*args, **kwargs)
        except Exception as e:
            retries += 1
            if retries >= MAX_RETRIES_PER_OPERATION:
                raise OperationFailedError(f"Failed after {retries} retries: {e}")
            time.sleep(2 ** retries)  # 指数退避
```

#### 建议3：添加状态验证步骤
```bash
# 修改后立即验证
modify_file() {
    local file="$1"
    local content="$2"
    
    # 记录修改前状态
    local before_hash=$(md5sum "$file" 2>/dev/null | cut -d' ' -f1)
    
    # 执行修改
    echo "$content" > "$file"
    
    # 验证修改生效
    local after_hash=$(md5sum "$file" | cut -d' ' -f1)
    if [ "$before_hash" = "$after_hash" ]; then
        echo "WARNING: File content unchanged after modification"
        return 1
    fi
    
    echo "SUCCESS: File modified successfully"
    return 0
}
```

## 四、兜底方案

当检测到循环或超过重试限制时：

1. **立即停止**当前操作序列
2. **保存现场**：记录当前状态、错误信息、操作历史
3. **切换到降级模式**：
   - 使用最近一次成功的文件版本
   - 跳过非关键步骤
   - 输出详细错误报告
4. **通知用户**：提供清晰的问题描述和建议的下一步操作

## 五、实施检查清单

- [x] 创建循环检测文档
- [ ] 在每次文件操作前添加检查逻辑
- [ ] 实现操作日志追踪
- [ ] 设置最大重试次数 (建议: 3)
- [ ] 添加指数退避重试策略
- [ ] 实现修改后状态验证
- [ ] 准备兜底方案脚本
- [ ] 定期审查操作日志
