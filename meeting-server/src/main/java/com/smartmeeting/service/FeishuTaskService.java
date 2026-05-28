package com.smartmeeting.service;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.repository.MeetingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuTaskService {

    private final FeishuService feishuService;
    private final MeetingMapper meetingMapper;

    public void syncTodosToFeishu(String meetingId, List<MeetingTodo> todos) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null || todos == null || todos.isEmpty()) {
            return;
        }
        String text = "会议【" + meeting.getTitle() + "】待办已同步，共 " + todos.size() + " 项。";
        if (meeting.getChatId() != null && !meeting.getChatId().isBlank()) {
            feishuService.sendMessage(meeting.getChatId(), text);
            return;
        }
        if (meeting.getCreatorId() != null) {
            feishuService.sendMessageToUserId(meeting.getCreatorId(), text);
        }
    }

    public void remindTodoOwner(MeetingTodo todo) {
        if (todo == null || todo.getAssigneeId() == null || todo.getAssigneeId().isBlank()) {
            return;
        }
        String text = "待办提醒：[" + todo.getAssigneeName() + "] " + todo.getContent();
        feishuService.sendMessageToUserId(todo.getAssigneeId(), text);
        log.info("Todo reminder sent: todoId={}, assignee={}", todo.getId(), todo.getAssigneeId());
    }
}
