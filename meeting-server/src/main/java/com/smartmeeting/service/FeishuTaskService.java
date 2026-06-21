package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuTaskService {

    private final FeishuService feishuService;
    private final MeetingMapper meetingMapper;
    private final FeishuCardBuilder cardBuilder;

    @Value("${meeting.base-url:http://localhost:8765}")
    private String baseUrl;

    public void syncTodosToFeishu(String meetingId, List<MeetingTodo> todos) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null || todos == null || todos.isEmpty()) {
            return;
        }
        String dashboardHint = baseUrl + "/dashboard.html";
        for (MeetingTodo todo : todos) {
            notifyTodoStakeholders(meeting, todo, dashboardHint);
        }
        String summary = "会议【" + meeting.getTitle() + "】待办已同步，共 " + todos.size() + " 项。";
        if (meeting.getChatId() != null && !meeting.getChatId().isBlank()) {
            feishuService.sendMessage(meeting.getChatId(), summary);
        }
    }

    private void notifyTodoStakeholders(Meeting meeting, MeetingTodo todo, String dashboardHint) {
        String assigneeId = todo.getAssigneeId();
        if (assigneeId != null && !assigneeId.isBlank() && !"unknown".equalsIgnoreCase(assigneeId)) {
            String cardJson = cardBuilder.buildTodoActionCard(todo);
            feishuService.sendInteractiveCardToUserId(assigneeId, cardJson);
        }
        String operatorId = todo.getOperatorId();
        if (operatorId != null && !operatorId.isBlank() && !"unknown".equalsIgnoreCase(operatorId)
                && !operatorId.equals(assigneeId)) {
            String text = "您有一项待办需经办：【" + todo.getContent() + "】\n"
                    + "请在智能会议前台「我的待办」更新进度或上传附件：\n" + dashboardHint;
            feishuService.sendMessageToUserId(operatorId, text);
        }
    }

    public void remindTodoOwner(MeetingTodo todo) {
        if (todo == null || todo.getAssigneeId() == null || todo.getAssigneeId().isBlank()) {
            return;
        }
        if (!"unknown".equalsIgnoreCase(todo.getAssigneeId())) {
            feishuService.sendInteractiveCardToUserId(todo.getAssigneeId(), cardBuilder.buildTodoActionCard(todo));
        }
        log.info("Todo reminder sent: todoId={}, assignee={}", todo.getId(), todo.getAssigneeId());
    }
}
