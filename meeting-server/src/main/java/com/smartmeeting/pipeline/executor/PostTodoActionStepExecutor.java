package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostTodoActionStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final PipelineExecutorSupport support;

    @Override
    public String stepType() {
        return "post-todo-action";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode shared = support.parseConfig(context.getSharedContextJson());
        String decision = shared.path("callbackPayload").path("decision").asText("");
        String todoId = shared.path("callbackPayload").path("todoId").asText("");
        if (decision.isBlank() || todoId.isBlank()) {
            return StepExecutionResult.ok("no-todo-action", "{\"updated\":0}");
        }
        MeetingTodo todo = todoMapper.selectOne(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .eq(MeetingTodo::getId, todoId)
                .last("LIMIT 1"));
        if (todo == null) {
            return StepExecutionResult.ok("todo-not-found", "{\"updated\":0}");
        }
        if ("complete_todo".equalsIgnoreCase(decision)) {
            todo.setStatus("COMPLETED");
        } else if ("delay_todo".equalsIgnoreCase(decision)) {
            todo.setStatus("DELAYED");
        } else {
            return StepExecutionResult.ok("decision-ignored", "{\"updated\":0}");
        }
        todoMapper.updateById(todo);
        return StepExecutionResult.ok("todo-action-updated", "{\"updated\":1,\"status\":\"" + todo.getStatus() + "\"}");
    }
}

