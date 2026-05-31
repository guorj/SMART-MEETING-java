package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.Priority;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.config.MeetingTodoProperties;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.notification.MeetingFeishuNotifier;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 待办提取服务：从会议纪要文本中识别 Action Items 并持久化。
 * <p>
 * 流程：调用 LLM 提取 JSON 格式待办 → 按姓名匹配参会人 userId → 写入 {@code int_meeting_todo}
 * → 更新参会人 todo_count → 通过 {@link MeetingFeishuNotifier} 推送飞书通知。
 * <p>
 * 主要协作组件：{@link MeetingMapper}、{@link ParticipantMapper}、{@link TodoMapper}、
 * {@link MeetingMinuteService}、{@link FeishuService}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoExtractionService {

    private final MeetingMapper meetingMapper;
    private final ParticipantMapper participantMapper;
    private final TodoMapper todoMapper;
    private final FeishuService feishuService;
    private final MeetingFeishuNotifier meetingFeishuNotifier;
    private final MeetingMinuteService meetingMinuteService;
    private final MeetingStateMachineService meetingStateMachineService;
    private final FeishuTaskService feishuTaskService;
    private final MeetingTodoProperties todoProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${meeting.llm.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String llmApiUrl;

    @Value("${meeting.llm.api-key:}")
    private String llmApiKey;

    @Value("${meeting.llm.model:deepseek-chat}")
    private String llmModel;

    /**
     * 从库内纪要提取待办（纪要文本从 {@link MeetingMinuteService} 加载）。
     *
     * @param meetingId 会议 ID
     * @return 已保存的待办列表；会议不存在或无纪要内容时返回空列表
     */
    @Transactional
    public List<MeetingTodo> extractTodos(String meetingId) {
        return extractTodos(meetingId, null);
    }

    /**
     * 从纪要文本提取待办并写入数据库。
     *
     * @param meetingId  会议 ID
     * @param minuteText 纪要全文；为空时从 {@code int_meeting_minute} 加载
     * @return 已保存的待办列表；会议不存在或无纪要内容时返回空列表
     */
    @Transactional
    public List<MeetingTodo> extractTodos(String meetingId, String minuteText) {
        if (!todoProperties.isExtractionEnabled()) {
            log.info("Todo extraction disabled by config, skip meetingId={}", meetingId);
            return List.of();
        }
        String resolved = resolveMinuteText(meetingId, minuteText);
        log.info("Extracting todos for meeting: {}, minute length={}", meetingId, resolved.length());
        if (resolved.isEmpty()) {
            log.warn("No minute content for todo extraction: meetingId={}", meetingId);
            return List.of();
        }

        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            log.warn("Meeting not found: {}", meetingId);
            return List.of();
        }

        // 获取参会人列表用于责任人匹配
        LambdaQueryWrapper<Participant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Participant::getMeetingId, meetingId);
        List<Participant> participants = participantMapper.selectList(wrapper);
        Map<String, String> nameToUserId = new HashMap<>();
        Map<String, Participant> nameToParticipant = new HashMap<>();
        for (Participant p : participants) {
            nameToUserId.put(p.getName(), p.getUserId());
            nameToParticipant.put(p.getName(), p);
        }

        // 调用 LLM 提取待办
        List<TodoItem> todoItems = callLLMExtractTodos(resolved, participants);
        log.info("LLM extracted {} todo items", todoItems.size());

        // 写入数据库
        List<MeetingTodo> savedTodos = new ArrayList<>();
        for (TodoItem item : todoItems) {
            MeetingTodo todo = new MeetingTodo();
            todo.setId(UUID.randomUUID().toString());
            todo.setMeetingId(meetingId);
            todo.setPresetTypeCode(meeting.getPresetTypeCode());
            todo.setContent(item.content);
            todo.setAssigneeName(item.assigneeName);

            // 匹配责任人 userId
            String assigneeId = nameToUserId.get(item.assigneeName);
            if (assigneeId == null) {
                // 尝试模糊匹配（名字的一部分）
                assigneeId = fuzzyMatchAssignee(item.assigneeName, nameToUserId);
            }
            todo.setAssigneeId(assigneeId != null ? assigneeId : "unknown");

            todo.setStatus(TodoStatus.PENDING.name());
            todo.setPriority(item.priority != null ? item.priority : Priority.MEDIUM.name());
            todo.setDeadline(item.deadline);
            todo.setRemindCount(0);
            todo.setCreatedAt(LocalDateTime.now());

            todoMapper.insert(todo);
            savedTodos.add(todo);

            // 更新参会人 todo_count
            if (assigneeId != null && nameToParticipant.containsKey(item.assigneeName)) {
                Participant p = nameToParticipant.get(item.assigneeName);
                p.setTodoCount(p.getTodoCount() + 1);
                participantMapper.updateById(p);
            }

            log.debug("Todo saved: id={}, content={}, assignee={}", todo.getId(), todo.getContent(), todo.getAssigneeName());
        }

        // 更新会议状态
        meetingStateMachineService.apply(meetingId, MeetingEvent.TODO_SYNCED);
        meeting.setStatus("TODO_TRACKING");
        meetingMapper.updateById(meeting);

        // 发送飞书待办通知
        if (!savedTodos.isEmpty() && meeting.getCreatorId() != null) {
            sendTodoNotification(meeting, savedTodos);
            feishuTaskService.syncTodosToFeishu(meetingId, savedTodos);
        }

        return savedTodos;
    }

    /**
     * 调用 LLM API 从纪要文本提取待办项；失败时降级为正则提取。
     *
     * @param minuteText   纪要全文
     * @param participants 参会人列表（用于 Prompt 中的姓名约束）
     * @return 解析后的待办项列表
     */
    private List<TodoItem> callLLMExtractTodos(String minuteText, List<Participant> participants) {
        // 构建参会人名单
        StringBuilder participantList = new StringBuilder();
        for (Participant p : participants) {
            participantList.append("- ").append(p.getName()).append("\n");
        }

        String prompt = buildExtractionPrompt(minuteText, participantList.toString());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", llmModel);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个会议待办提取助手，请从会议纪要中提取待办事项。"),
                    Map.of("role", "user", "content", prompt)
            ));
            body.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(llmApiUrl + "/v1/chat/completions", request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseLLMResponse(response.getBody());
            }
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            // 降级：正则提取
            return fallbackExtract(minuteText);
        }

        return List.of();
    }

    /**
     * 构建 LLM 待办提取 Prompt，约束输出为 JSON 数组格式。
     *
     * @param minuteText      纪要全文
     * @param participantList 参会人名单文本
     * @return 完整 Prompt 字符串
     */
    private String buildExtractionPrompt(String minuteText, String participantList) {
        return """
请从以下会议纪要中提取所有待办事项（Action Items），以JSON数组格式输出。

参会人名单:
%s

输出格式要求（严格遵守JSON格式，不要输出任何其他文字）:
[
  {
    "content": "待办内容",
    "assigneeName": "责任人姓名（必须是参会人名单中的姓名）",
    "priority": "HIGH|MEDIUM|LOW",
    "deadline": "截止日期（YYYY-MM-DD格式，如无明确日期则留空）"
  }
]

会议纪要:
%s
""".formatted(participantList, minuteText);
    }

    /**
     * 解析 LLM 响应体中的 JSON 待办数组，含三层容错（直接解析、正则提取、降级正则）。
     *
     * @param responseBody LLM HTTP 响应体
     * @return 待办项列表；解析失败时返回空列表或降级结果
     */
    private List<TodoItem> parseLLMResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            String text = content.asText();

            // 清理可能的 Markdown 包装
            text = text.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "").trim();

            // 三层容错解析
            try {
                // 尝试直接解析 JSON 数组
                JsonNode array = objectMapper.readTree(text);
                if (array.isArray()) {
                    return parseJsonArray(array);
                }
            } catch (Exception e1) {
                // 尝试正则提取 JSON 数组
                Pattern pattern = Pattern.compile("\\[\\s*\\{[^\\]]+\\}\\s*\\]");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    JsonNode array = objectMapper.readTree(matcher.group());
                    return parseJsonArray(array);
                }
            }

            log.warn("Failed to parse LLM response as JSON, fallback to regex");
            return fallbackExtract(text);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将 JSON 数组节点逐条映射为 {@link TodoItem}，过滤 content 或 assigneeName 为空的项。
     *
     * @param array LLM 返回的待办 JSON 数组
     * @return 有效待办项列表
     */
    private List<TodoItem> parseJsonArray(JsonNode array) {
        List<TodoItem> items = new ArrayList<>();
        for (JsonNode node : array) {
            TodoItem item = new TodoItem();
            item.content = node.path("content").asText("");
            item.assigneeName = node.path("assigneeName").asText("");
            String priority = node.path("priority").asText("MEDIUM");
            item.priority = priority.toUpperCase();
            String deadline = node.path("deadline").asText("");
            if (!deadline.isEmpty()) {
                try {
                    item.deadline = LocalDateTime.parse(deadline + "T18:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (Exception e) {
                    log.warn("Failed to parse deadline: {}", deadline);
                }
            }
            if (!item.content.isEmpty() && !item.assigneeName.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 降级方案：通过正则从文本中提取「待办/Action + 责任人」模式。
     *
     * @param text 待解析文本
     * @return 正则匹配到的待办项列表
     */
    private List<TodoItem> fallbackExtract(String text) {
        List<TodoItem> items = new ArrayList<>();
        // 匹配常见的待办模式："- 张三：完成XXX" 或 "待办：XXX（责任人：张三）"
        Pattern pattern = Pattern.compile("(?:待办|Action)[-：:\\s]+(.+?)(?:\\s*[（(]责任人[：:]+([^）)]+)[）)]|\\s+责任人[：:]+([^\\s]+))");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            TodoItem item = new TodoItem();
            item.content = matcher.group(1).trim();
            item.assigneeName = matcher.group(2) != null ? matcher.group(2).trim() : matcher.group(3).trim();
            item.priority = "MEDIUM";
            items.add(item);
        }
        return items;
    }

    /**
     * 模糊匹配责任人姓名（子串包含关系）。
     *
     * @param name          LLM 返回的责任人姓名
     * @param nameToUserId  参会人姓名 → userId 映射
     * @return 匹配到的 userId；无匹配时返回 {@code null}
     */
    private String fuzzyMatchAssignee(String name, Map<String, String> nameToUserId) {
        for (Map.Entry<String, String> entry : nameToUserId.entrySet()) {
            if (entry.getKey().contains(name) || name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 向会议群聊（或创建人）推送待办同步飞书卡片。
     *
     * @param meeting 会议实体
     * @param todos   已保存的待办列表
     */
    private void sendTodoNotification(Meeting meeting, List<MeetingTodo> todos) {
        // 优先使用 chatId（群聊），否则降级使用 creatorId（可能失败）
        String targetId = meeting.getChatId() != null ? meeting.getChatId() : meeting.getCreatorId();
        if (meeting.getChatId() == null) {
            log.warn("No chatId for meeting {}, fallback to creatorId - may fail", meeting.getId());
        }
        
        StringBuilder content = new StringBuilder();
        content.append("## 📋 待办已同步\n\n");
        content.append("会议: **").append(meeting.getTitle()).append("**\n\n");
        content.append("提取待办 **").append(todos.size()).append("** 项:\n\n");

        for (MeetingTodo todo : todos) {
            content.append("- ").append(todo.getContent());
            content.append("（责任人: ").append(todo.getAssigneeName()).append("）\n");
        }

        // 构建卡片元素
        List<Map<String, String>> elements = new ArrayList<>();
        Map<String, String> mainContent = new HashMap<>();
        mainContent.put("content", content.toString());
        elements.add(mainContent);

        meetingFeishuNotifier.sendCardMessage(
                targetId, "📋 待办已同步", elements,
                "TODO_SYNC", meeting.getId(), "todo-sync:" + meeting.getId());
        log.info("Todo notification sent to: {}", targetId);
    }

    /**
     * 解析纪要文本：优先使用入参，否则从 {@link MeetingMinuteService} 加载。
     *
     * @param meetingId  会议 ID
     * @param minuteText 外部传入的纪要文本（可为 null）
     * @return 非空纪要文本，无内容时返回空字符串
     */
    private String resolveMinuteText(String meetingId, String minuteText) {
        if (minuteText != null && !minuteText.isBlank()) {
            return minuteText.trim();
        }
        return meetingMinuteService.getContentMarkdownOrEmpty(meetingId);
    }

    /** LLM 提取结果的内部数据结构。 */
    private static class TodoItem {
        String content;
        String assigneeName;
        String priority;
        LocalDateTime deadline;
    }
}