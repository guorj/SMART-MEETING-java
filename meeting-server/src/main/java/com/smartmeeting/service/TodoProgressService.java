package com.smartmeeting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.api.dto.TodoProgressCreateRequest;
import com.smartmeeting.api.dto.TodoProgressResponse;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.MeetingTodoProgress;
import com.smartmeeting.enums.TodoAuthorRole;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.exception.BusinessException;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.repository.TodoProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TodoProgressService {

    private final TodoMapper todoMapper;
    private final TodoProgressMapper progressMapper;
    private final TodoPermissionService todoPermissionService;

    public List<TodoProgressResponse> listProgress(String todoId, String feishuUserId) {
        MeetingTodo todo = requireTodo(todoId);
        todoPermissionService.requireAssigneeOrOperator(todo, feishuUserId);
        LambdaQueryWrapper<MeetingTodoProgress> w = new LambdaQueryWrapper<>();
        w.eq(MeetingTodoProgress::getTodoId, todoId)
                .orderByAsc(MeetingTodoProgress::getCreatedAt);
        return progressMapper.selectList(w).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TodoProgressResponse addProgress(String todoId, TodoProgressCreateRequest request,
                                            String feishuUserId, String userName) {
        MeetingTodo todo = requireTodo(todoId);
        TodoAuthorRole role = todoPermissionService.resolveRole(todo, feishuUserId);
        if (role == null) {
            throw new BusinessException(403, "仅责任人或经办人可更新进度");
        }

        if (request.getProgressPercent() != null) {
            int pct = request.getProgressPercent();
            if (pct < 0 || pct > 100) {
                throw new BusinessException(400, "progressPercent 须在 0-100 之间");
            }
        }

        MeetingTodoProgress row = new MeetingTodoProgress();
        row.setId(UUID.randomUUID().toString());
        row.setTodoId(todoId);
        row.setAuthorId(feishuUserId);
        row.setAuthorName(userName);
        row.setAuthorRole(role.name());
        row.setProgressText(request.getProgressText().trim());
        row.setProgressPercent(request.getProgressPercent());
        row.setCreatedAt(LocalDateTime.now());
        progressMapper.insert(row);

        if (TodoStatus.PENDING.name().equals(todo.getStatus())) {
            todo.setStatus(TodoStatus.IN_PROGRESS.name());
            todoMapper.updateById(todo);
        }

        return toResponse(row);
    }

    private MeetingTodo requireTodo(String todoId) {
        MeetingTodo todo = todoMapper.selectById(todoId);
        if (todo == null) {
            throw new BusinessException(404, "待办不存在: " + todoId);
        }
        return todo;
    }

    private TodoProgressResponse toResponse(MeetingTodoProgress p) {
        return TodoProgressResponse.builder()
                .id(p.getId())
                .todoId(p.getTodoId())
                .authorId(p.getAuthorId())
                .authorName(p.getAuthorName())
                .authorRole(p.getAuthorRole())
                .progressText(p.getProgressText())
                .progressPercent(p.getProgressPercent())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
