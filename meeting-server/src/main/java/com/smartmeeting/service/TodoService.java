package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.MeetingTodoResponse;
import com.smartmeeting.api.dto.TodoAssignRequest;
import com.smartmeeting.api.dto.TodoBoardResponse;
import com.smartmeeting.api.dto.TodoStatusUpdateRequest;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.statemachine.MeetingEvent;
import com.smartmeeting.statemachine.MeetingStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会议待办（Todo）查询与状态维护服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final MeetingMapper meetingMapper;
    private final TodoMapper todoMapper;
    private final ParticipantMapper participantMapper;
    private final MeetingStateMachineService meetingStateMachineService;
    private final TodoPermissionService todoPermissionService;

    private static final List<String> STATUS_SORT_ORDER = List.of(
            TodoStatus.PENDING.name(),
            TodoStatus.IN_PROGRESS.name(),
            TodoStatus.DELAYED.name(),
            TodoStatus.BLOCKED.name(),
            TodoStatus.OVERDUE.name(),
            TodoStatus.COMPLETED.name()
    );

    public MeetingTodo requireTodo(String todoId) {
        MeetingTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在: " + todoId);
        }
        return todo;
    }

    public MeetingTodoResponse getTodo(String todoId, String feishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssigneeOrOperator(todo, feishuUserId);
        return toResponse(todo);
    }

    public List<MeetingTodoResponse> listTodosByMeeting(String meetingId) {
        requireMeeting(meetingId);
        List<MeetingTodo> rows = selectTodosForMeeting(meetingId);
        sortTodos(rows);
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<MeetingTodoResponse> listMyTodos(String feishuUserId) {
        LambdaQueryWrapper<MeetingTodo> w = new LambdaQueryWrapper<>();
        w.and(q -> q.eq(MeetingTodo::getAssigneeId, feishuUserId)
                .or().eq(MeetingTodo::getOperatorId, feishuUserId))
                .ne(MeetingTodo::getStatus, TodoStatus.COMPLETED.name());
        List<MeetingTodo> rows = todoMapper.selectList(w);
        sortTodos(rows);
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public TodoBoardResponse getTodoBoard(String meetingId) {
        Meeting meeting = meetingMapper.selectById(meetingId);
        if (meeting == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
        List<MeetingTodo> rows = selectTodosForMeeting(meetingId);
        sortTodos(rows);

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        for (TodoStatus s : TodoStatus.values()) {
            statusCounts.put(s.name(), 0);
        }
        for (MeetingTodo t : rows) {
            statusCounts.merge(t.getStatus(), 1, Integer::sum);
        }

        return TodoBoardResponse.builder()
                .meetingId(meetingId)
                .meetingTitle(meeting.getTitle())
                .statusCounts(statusCounts)
                .todos(rows.stream().map(this::toResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public MeetingTodoResponse updateStatus(String todoId, TodoStatusUpdateRequest request, String feishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssignee(todo, feishuUserId);
        return applyStatusChange(todo, request);
    }

    /**
     * 飞书卡片或 Pipeline 回调更新状态（校验责任人）。
     */
    @Transactional
    public MeetingTodoResponse updateStatusByAssigneeCallback(String todoId, TodoStatusUpdateRequest request,
                                                              String operatorFeishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssignee(todo, operatorFeishuUserId);
        return applyStatusChange(todo, request);
    }

    @Transactional
    public MeetingTodoResponse assign(String todoId, TodoAssignRequest request) {
        MeetingTodo todo = requireTodo(todoId);
        todo.setAssigneeId(request.getAssigneeId().trim());
        if (request.getAssigneeName() != null) {
            todo.setAssigneeName(request.getAssigneeName());
        }
        todoMapper.updateById(todo);
        log.info("Todo assignee updated: id={}, assigneeId={}", todoId, todo.getAssigneeId());
        return toResponse(todo);
    }

    private MeetingTodoResponse applyStatusChange(MeetingTodo todo, TodoStatusUpdateRequest request) {
        final TodoStatus newStatus;
        try {
            newStatus = TodoStatus.valueOf(request.getStatus().trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "无效的待办状态: " + request.getStatus());
        }

        String oldStatus = todo.getStatus();
        todoPermissionService.validateAssigneeStatusTransition(oldStatus, newStatus);

        todo.setStatus(newStatus.name());

        if (newStatus == TodoStatus.COMPLETED) {
            todo.setCompletedAt(LocalDateTime.now());
        } else {
            todo.setCompletedAt(null);
        }

        if (request.getCompletionNote() != null) {
            todo.setCompletionNote(request.getCompletionNote());
        }
        if (request.getBlockReason() != null) {
            todo.setBlockReason(request.getBlockReason());
        } else if (newStatus != TodoStatus.DELAYED && newStatus != TodoStatus.BLOCKED) {
            todo.setBlockReason(null);
        }

        todoMapper.updateById(todo);

        String meetingId = todo.getMeetingId();
        String assigneeId = todo.getAssigneeId();
        if (TodoStatus.COMPLETED.name().equals(newStatus.name()) && !TodoStatus.COMPLETED.name().equals(oldStatus)) {
            adjustParticipantCompletedCount(meetingId, assigneeId, 1);
        } else if (!TodoStatus.COMPLETED.name().equals(newStatus.name()) && TodoStatus.COMPLETED.name().equals(oldStatus)) {
            adjustParticipantCompletedCount(meetingId, assigneeId, -1);
        }
        markAllDoneIfNeeded(meetingId);

        log.info("Todo status updated: id={}, {} -> {}", todo.getId(), oldStatus, newStatus.name());
        return toResponse(todo);
    }

    /**
     * Admin 强制改状态（无权限校验，由 Admin 层写审计后调用）。
     */
    @Transactional
    public MeetingTodoResponse forceUpdateStatus(String todoId, TodoStatusUpdateRequest request) {
        MeetingTodo todo = requireTodo(todoId);
        final TodoStatus newStatus;
        try {
            newStatus = TodoStatus.valueOf(request.getStatus().trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "无效的待办状态: " + request.getStatus());
        }
        if (newStatus == TodoStatus.OVERDUE) {
            throw new BusinessException(400, "OVERDUE 状态由系统维护，不可手动设置");
        }
        return applyStatusChange(todo, request);
    }

    public void adjustParticipantCompletedCount(String meetingId, String assigneeId, int delta) {
        if (assigneeId == null || assigneeId.isBlank() || "unknown".equalsIgnoreCase(assigneeId)) {
            return;
        }
        LambdaQueryWrapper<Participant> w = new LambdaQueryWrapper<>();
        w.eq(Participant::getMeetingId, meetingId).eq(Participant::getUserId, assigneeId);
        Participant p = participantMapper.selectOne(w);
        if (p == null) {
            return;
        }
        int cc = p.getCompletedCount() == null ? 0 : p.getCompletedCount();
        p.setCompletedCount(Math.max(0, cc + delta));
        participantMapper.updateById(p);
    }

    private void requireMeeting(String meetingId) {
        if (meetingMapper.selectById(meetingId) == null) {
            throw new BusinessException(404, "会议不存在: " + meetingId);
        }
    }

    private List<MeetingTodo> selectTodosForMeeting(String meetingId) {
        LambdaQueryWrapper<MeetingTodo> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodo::getMeetingId, meetingId);
        return todoMapper.selectList(w);
    }

    private void sortTodos(List<MeetingTodo> rows) {
        Comparator<MeetingTodo> cmp = Comparator
                .comparingInt((MeetingTodo t) -> statusRank(t.getStatus()))
                .thenComparing(MeetingTodo::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MeetingTodo::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        rows.sort(cmp);
    }

    private int statusRank(String status) {
        int i = STATUS_SORT_ORDER.indexOf(status);
        return i < 0 ? 999 : i;
    }

    public MeetingTodoResponse toResponse(MeetingTodo t) {
        return MeetingTodoResponse.builder()
                .id(t.getId())
                .meetingId(t.getMeetingId())
                .content(t.getContent())
                .assigneeId(t.getAssigneeId())
                .assigneeName(t.getAssigneeName())
                .operatorId(t.getOperatorId())
                .operatorName(t.getOperatorName())
                .status(t.getStatus())
                .priority(t.getPriority())
                .deadline(t.getDeadline())
                .completedAt(t.getCompletedAt())
                .completionNote(t.getCompletionNote())
                .blockReason(t.getBlockReason())
                .lastRemindAt(t.getLastRemindAt())
                .remindCount(t.getRemindCount())
                .nextMeetingId(t.getNextMeetingId())
                .reportedInNext(t.getReportedInNext())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private void markAllDoneIfNeeded(String meetingId) {
        LambdaQueryWrapper<MeetingTodo> openWrapper = new LambdaQueryWrapper<>();
        openWrapper.eq(MeetingTodo::getMeetingId, meetingId)
                .ne(MeetingTodo::getStatus, TodoStatus.COMPLETED.name());
        Long remain = todoMapper.selectCount(openWrapper);
        if (remain != null && remain == 0L) {
            try {
                meetingStateMachineService.apply(meetingId, MeetingEvent.ALL_TODOS_DONE);
                Meeting meeting = meetingMapper.selectById(meetingId);
                if (meeting != null) {
                    meeting.setStatus("ALL_DONE");
                    meetingMapper.updateById(meeting);
                }
                log.info("Meeting moved to ALL_DONE automatically: {}", meetingId);
            } catch (Exception e) {
                log.debug("Skip ALL_DONE transition for meeting {}: {}", meetingId, e.getMessage());
            }
        }
    }
}
