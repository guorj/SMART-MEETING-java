package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuService;
import com.smartmeeting.service.FeishuTaskService;
import com.smartmeeting.pipeline.executor.PipelineExecutorSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * POST 阶段飞书任务同步执行器。
 * <p>
 * 对会议下所有未完成待办执行两步同步：
 * <ol>
 *   <li>调 {@link FeishuService#createTask} 同步到飞书任务中心（单向，便于责任人在飞书任务列表查看）</li>
 *   <li>调 {@link FeishuTaskService#notifyTodoStakeholders}（间接通过 remindTodoOwner）
 *       向责任人推送交互卡片（含"提交完成/挂起/更新进度"按钮）</li>
 * </ol>
 * </p>
 * <p>
 * 立即 SUCCESS，不 waitForCallback（多待办多回调不适合 Pipeline 单 callbackKey 模型）；
 * 责任人点击卡片按钮后走 {@code FeishuCommandHandler.handleTodoCardAction} 直通 {@code TodoService}。
 * </p>
 * <p>
 * contextJson 写入 {@code {total, success, todoIds:[...]}}，供下游 step（如裁决超时扫描）参考。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostFeishuTaskSyncStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final FeishuService feishuService;
    private final FeishuTaskService feishuTaskService;
    private final PipelineExecutorSupport support;

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
        List<String> todoIds = new ArrayList<>();
        for (MeetingTodo todo : todos) {
            // 1) 同步到飞书任务中心（单向，便于责任人在飞书任务列表查看）
            FeishuService.TaskCreateResult result = feishuService.createTask(
                    todo.getContent(),
                    todo.getAssigneeId(),
                    todo.getDeadline(),
                    "meeting:" + context.getMeetingId() + ":" + todo.getId());
            // 2) 向责任人推送交互卡片（含提交完成/挂起/更新进度按钮）
            try {
                feishuTaskService.remindTodoOwner(todo);
            } catch (Exception e) {
                log.warn("push todo action card failed: todoId={}, err={}", todo.getId(), e.getMessage());
            }
            if (result.success()) {
                success++;
            }
            todoIds.add(todo.getId());
        }
        ObjectNode out = support.newObject();
        out.put("total", todos.size());
        out.put("success", success);
        ArrayNode ids = out.putArray("todoIds");
        for (String id : todoIds) {
            ids.add(id);
        }
        return StepExecutionResult.ok("post-feishu-task-sync", support.toJson(out));
    }
}
