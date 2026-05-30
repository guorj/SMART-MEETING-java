package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostTodoRemindStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final FeishuTaskService feishuTaskService;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "post-todo-remind";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        int limit = support.number(support.parseConfig(context.getStep().getConfigJson()), "limit", 50);
        List<MeetingTodo> rows = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .in(MeetingTodo::getStatus, List.of("PENDING", "IN_PROGRESS", "DELAYED", "BLOCKED", "OVERDUE"))
                .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
        int reminded = 0;
        for (MeetingTodo todo : rows) {
            feishuTaskService.remindTodoOwner(todo);
            todo.setLastRemindAt(LocalDateTime.now());
            Integer rc = todo.getRemindCount() == null ? 0 : todo.getRemindCount();
            todo.setRemindCount(rc + 1);
            todoMapper.updateById(todo);
            reminded++;
        }
        return StepExecutionResult.ok("post-todo-remind-ok", "{\"reminded\":" + reminded + "}");
    }
}

