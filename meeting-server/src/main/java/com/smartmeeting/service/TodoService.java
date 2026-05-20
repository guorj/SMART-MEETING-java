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
 *
 * <p>提供按会议列出待办、看板聚合统计、状态流转与责任人指派；状态变为已完成时
 * 同步调整参会人 {@code completedCount}。
 *
 * <p>主要协作：{@link com.smartmeeting.repository.MeetingMapper}、
 * {@link com.smartmeeting.repository.TodoMapper}、
 * {@link com.smartmeeting.repository.ParticipantMapper}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final MeetingMapper meetingMapper;
    private final TodoMapper todoMapper;
    private final ParticipantMapper participantMapper;

    private static final List<String> STATUS_SORT_ORDER = List.of(
            TodoStatus.PENDING.name(),
            TodoStatus.IN_PROGRESS.name(),
            TodoStatus.DELAYED.name(),
            TodoStatus.BLOCKED.name(),
            TodoStatus.OVERDUE.name(),
            TodoStatus.COMPLETED.name()
    );

    /**
     * 列出指定会议的全部待办（按状态、截止日、创建时间排序）。
     *
     * @param meetingId 会议 ID
     * @return 待办 DTO 列表
     * @throws BusinessException 会议不存在时（404）
     */
    public List<MeetingTodoResponse> listTodosByMeeting(String meetingId) {
        requireMeeting(meetingId);
        List<MeetingTodo> rows = selectTodosForMeeting(meetingId);
        sortTodos(rows);
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * 获取会议待办看板（含各状态计数与排序后的待办列表）。
     *
     * @param meetingId 会议 ID
     * @return 看板响应
     * @throws BusinessException 会议不存在时（404）
     */
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

    /**
     * 更新待办状态及完成说明、阻塞原因等附属字段。
     *
     * @param todoId  待办 ID
     * @param request 含目标状态及可选备注
     * @return 更新后的待办 DTO
     * @throws BusinessException 待办不存在（404）或状态非法（400）
     */
    @Transactional
    public MeetingTodoResponse updateStatus(String todoId, TodoStatusUpdateRequest request) {
        MeetingTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在: " + todoId);
        }

        final TodoStatus newStatus;
        try {
            newStatus = TodoStatus.valueOf(request.getStatus().trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "无效的待办状态: " + request.getStatus());
        }

        String oldStatus = todo.getStatus();
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

        log.info("Todo status updated: id={}, {} -> {}", todoId, oldStatus, newStatus.name());
        return toResponse(todo);
    }

    /**
     * 指派或变更待办责任人。
     *
     * @param todoId  待办 ID
     * @param request 含 {@code assigneeId} 及可选 {@code assigneeName}
     * @return 更新后的待办 DTO
     * @throws BusinessException 待办不存在时（404）
     */
    @Transactional
    public MeetingTodoResponse assign(String todoId, TodoAssignRequest request) {
        MeetingTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在: " + todoId);
        }
        todo.setAssigneeId(request.getAssigneeId().trim());
        if (request.getAssigneeName() != null) {
            todo.setAssigneeName(request.getAssigneeName());
        }
        todoMapper.updateById(todo);
        log.info("Todo assignee updated: id={}, assigneeId={}", todoId, todo.getAssigneeId());
        return toResponse(todo);
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

    /**
     * 按待办完成状态变化增减参会人已完成待办计数。
     *
     * @param meetingId  会议 ID
     * @param assigneeId 责任人用户 ID（空或 unknown 时忽略）
     * @param delta      增量（+1 或 -1）
     */
    private void adjustParticipantCompletedCount(String meetingId, String assigneeId, int delta) {
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

    private MeetingTodoResponse toResponse(MeetingTodo t) {
        return MeetingTodoResponse.builder()
                .id(t.getId())
                .meetingId(t.getMeetingId())
                .content(t.getContent())
                .assigneeId(t.getAssigneeId())
                .assigneeName(t.getAssigneeName())
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
}
