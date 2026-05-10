# AI Agent 集成指南 - 方案B实现

## 已完成的代码

### 1. 新增文件
- ✅ `AiAgentService.java` - OpenClaw Agent调用服务
- ✅ `MeetingProgressAIEnhancer.java` - 环节1：上次会议进度智能分析
- ✅ `MinuteAIEnhancer.java` - 环节2：纪要质量优化增强
- ✅ `application.yml` - OpenClaw配置已添加

---

## 需要手动修改的代码

### 环节1：MeetingService.java - 上次会议进度通报

**修改位置**: `startMeeting()` 方法，约第95-130行

**修改前**:
```java
if (meeting.getPreviousMeetingId() != null) {
    Meeting previous = meetingMapper.selectById(meeting.getPreviousMeetingId());
    if (previous != null) {
        // ... 查询待办统计
        // ... 构建简单卡片（固定格式）
        feishuService.sendCardMessage(targetId, "📊 待办进度通报", elements);
    }
}
```

**修改后**:
```java
// 需要在类开头注入：
private final MeetingProgressAIEnhancer progressAIEnhancer;

// 修改 startMeeting() 中的进度通报部分：
if (meeting.getPreviousMeetingId() != null) {
    Meeting previous = meetingMapper.selectById(meeting.getPreviousMeetingId());
    if (previous != null) {
        meeting.setStatus(MeetingStatus.REVIEWING.name());
        log.info("Previous meeting found: {}, will send progress card", previous.getId());
        
        // 查询上次会议待办统计
        LambdaQueryWrapper<MeetingTodo> todoWrapper = new LambdaQueryWrapper<>();
        todoWrapper.eq(MeetingTodo::getMeetingId, previous.getId());
        List<MeetingTodo> todos = todoMapper.selectList(todoWrapper);
        
        // 🤖 【环节1介入】调用AI分析上次会议待办进度
        List<Map<String, String>> elements;
        try {
            String aiAnalysis = progressAIEnhancer.analyzeAndEnhance(
                meeting.getId(),
                previous.getId(),
                previous.getTitle(),
                todos
            );
            
            if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                // 使用AI智能分析结果构建卡片
                elements = progressAIEnhancer.buildSmartCardElements(aiAnalysis, previous.getTitle());
                log.info("Smart progress card built with AI analysis");
            } else {
                // AI失败，降级使用简单卡片
                int completed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.COMPLETED.name())).count();
                int inProgress = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.IN_PROGRESS.name())).count();
                int delayed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.DELAYED.name())).count();
                elements = progressAIEnhancer.buildFallbackCardElements(previous.getTitle(), completed, inProgress, delayed, todos);
                log.info("Fallback to simple progress card");
            }
        } catch (Exception e) {
            log.warn("AI enhancement failed, fallback: {}", e.getMessage());
            // 降级
            int completed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.COMPLETED.name())).count();
            int inProgress = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.IN_PROGRESS.name())).count();
            int delayed = (int) todos.stream().filter(t -> t.getStatus().equals(TodoStatus.DELAYED.name())).count();
            elements = progressAIEnhancer.buildFallbackCardElements(previous.getTitle(), completed, inProgress, delayed, todos);
        }
        
        // 推送卡片
        String targetId = meeting.getChatId() != null ? meeting.getChatId() : meeting.getCreatorId();
        feishuService.sendCardMessage(targetId, "📊 待办进度通报", elements);
    }
}
```

---

### 环节2：MinuteGenerationService.java - 纪要优化

**修改位置**: `generateMinute()` 方法，约第50-70行

**修改前**:
```java
// 调用 LLM 生成纪要
minuteText = generateMinuteByLLM(meeting, correctedText, participants);
```

**修改后**:
```java
// 需要在类开头注入：
private final MinuteAIEnhancer minuteAIEnhancer;

// 修改 generateMinute() 中的纪要生成部分：
// 4. 调用 LLM 生成纪要
log.info("Step 4: LLM minute generation...");
minuteText = generateMinuteByLLM(meeting, correctedText, participants);
log.info("Step 4: LLM generation completed, text length={}", minuteText.length());

// 🤖 【环节2介入】调用AI优化纪要质量
try {
    String participantsNames = participants.stream()
        .map(Participant::getName)
        .collect(Collectors.joining(","));
    
    minuteText = minuteAIEnhancer.enhanceMinute(
        meetingId,
        minuteText,              // LLM初版纪要
        meeting.getTitle(),
        meeting.getPresetTypeCode(),
        participantsNames,
        correctedText            // 转写原文（用于校验）
    );
    
    log.info("Step 4.1: AI minute enhancement completed");
} catch (Exception e) {
    log.warn("AI enhancement failed, use original minute: {}", e.getMessage());
    // 失败时保持原纪要
}
```

---

## 配置说明

### application.yml 新增配置
```yaml
openclaw:
  enabled: true                    # 是否启用AI Agent
  gateway-url: http://localhost:3000  # OpenClaw Gateway地址
  agent-session-key: data_manager  # 我的session标识
  timeout-seconds: 60              # 调用超时时间
```

### 环境变量配置（生产环境）
```bash
export OPENCLAW_ENABLED=true
export OPENCLAW_GATEWAY_URL=http://your-openclaw-server:3000
export OPENCLAW_SESSION_KEY=data_manager
export OPENCLAW_TIMEOUT=60
```

---

## 效果对比

### 环节1：上次会议进度通报

| 维度 | 原方案 | AI增强后 |
|------|--------|----------|
| 卡片内容 | 数字统计 + 延期项列表 | 深度分析 + 建议 + 重点提醒 |
| 延期处理 | 简单罗列 | 原因归类分析 |
| 下次会议 | 无关联建议 | 推进建议 + 关注事项 |
| 智能程度 | 固定模板 | 动态洞察 |

**AI增强后的卡片示例**:
```
📊 上次会议待办进度

**综合管理会（周会）**

上次会议待办整体推进良好，但有2项关键延期需要重点关注。
延期主要集中在资源配置类事项，建议本次会议优先讨论。

⚠️ 延期原因分析:
- **资源配置不足**: 人员调配未到位 (2项)
- **外部依赖**: 等待供应商反馈 (1项)

🔥 高优先级待办提醒:
🔴 招聘渠道拓展需本周完成 (责任人: 管小慧)
🔴 职代会成立方案待确定 (责任人: 管小慧, 陈婉韵)

💡 本次会议推进建议:
- 优先讨论延期事项的解决方案
- 确认资源配置是否到位
- 明确外部依赖的跟进时间点

🎯 本次会议重点关注:
- 职代会成立进度
- 招聘渠道拓展方案
- 延期事项解决方案
```

---

### 环节2：纪要优化

| 维度 | 原方案 | AI增强后 |
|------|--------|----------|
| 纪要质量 | LLM通用格式 | 针对会议类型优化 |
| 信息完整性 | 无检查 | 质量评分 + 缺失提醒 |
| 待办标注 | 简单提取 | 优先级标注 + 重点高亮 |
| 发言整理 | 按时间顺序 | 按发言人归类 |

---

## 测试方法

### 1. 本地测试（需要启动OpenClaw）
```bash
# 启动OpenClaw Gateway
openclaw gateway start

# 启动smart-meeting-java
mvn spring-boot:run

# 创建会议，观察进度通报卡片内容变化
# 结束会议，观察纪要内容是否优化
```

### 2. 禁用AI（降级测试）
```bash
export OPENCLAW_ENABLED=false
# 系统会降级到原有逻辑
```

---

## 故障处理

### AI调用失败时
- **环节1**: 自动降级到原有简单卡片
- **环节2**: 使用LLM原纪要，不优化

### 超时处理
- 默认60秒超时
- 超时后自动降级
- 不阻塞主流程

---

## 后续可扩展

1. **更多介入环节**:
   - 实时问答助手
   - 待办督办提醒
   - 会议进程辅助

2. **AI增强能力**:
   - 纪要风格定制（正式/简洁）
   - 待办智能分配建议
   - 会议效率分析报告

---

## 关键文件清单

| 文件 | 作用 | 状态 |
|------|------|------|
| AiAgentService.java | OpenClaw调用封装 | ✅ 已创建 |
| MeetingProgressAIEnhancer.java | 环节1增强 | ✅ 已创建 |
| MinuteAIEnhancer.java | 环节2增强 | ✅ 已创建 |
| application.yml | OpenClaw配置 | ✅ 已添加 |
| MeetingService.java | 环节1集成 | ⚠️ 需手动修改 |
| MinuteGenerationService.java | 环节2集成 | ⚠️ 需手动修改 |