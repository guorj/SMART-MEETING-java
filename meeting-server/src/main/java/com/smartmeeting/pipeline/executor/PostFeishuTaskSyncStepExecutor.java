package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostFeishuTaskSyncStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "post-feishu-task-sync";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        List<MeetingTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .in(MeetingTodo::getStatus, List.of("PENDING", "IN_PROGRESS", "DELAYED", "BLOCKED", "OVERDUE")));
        int success = 0;
        for (MeetingTodo todo : todos) {
            FeishuService.TaskCreateResult result = feishuService.createTask(
                    todo.getContent(),
                    todo.getAssigneeId(),
                    todo.getDeadline(),
                    "meeting:" + context.getMeetingId() + ":" + todo.getId());
            if (result.success()) {
                success++;
            }
        }
        return StepExecutionResult.ok("post-feishu-task-sync", "{\"total\":" + todos.size() + ",\"success\":" + success + "}");
    }
}

