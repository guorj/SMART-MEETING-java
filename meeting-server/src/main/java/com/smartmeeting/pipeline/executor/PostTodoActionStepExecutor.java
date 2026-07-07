package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.enums.TodoStatus;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * POST 阶段待办裁决超时扫描执行器。
 * <p>
 * 扫描当前会议下处于 {@link TodoStatus#PENDING_DECISION} 态、且提交超过 N 天仍未裁决的待办，
 * 向决策人重发裁决催办卡片。
 * </p>
 * <p>
 * <b>config_json 配置项</b>：
 * <ul>
 *   <li>{@code timeoutDays}（int，默认 3）：裁决超时阈值，单位天</li>
 * </ul>
 * </p>
 * <p>
 * <b>contextJson 输出</b>：{@code {scanned, reminded}}
 * </p>
 * <p>
 * <b>历史说明</b>：本执行器原设计为消费 Pipeline callbackPayload 更新待办状态，
 * 但 todo-action 卡片回调实际走 {@code FeishuCommandHandler} 直通 {@code TodoService}，
 * 不经过 PipelineCallbackRouter，故原实现形同虚设。v0.27 起重新定义为裁决超时扫描。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostTodoActionStepExecutor implements StepExecutor {

    private static final int DEFAULT_TIMEOUT_DAYS = 3;

    private final TodoMapper todoMapper;
    private final PipelineExecutorSupport support;
    private final FeishuTaskService feishuTaskService;

    @Override
    public String stepType() {
        return "post-todo-action";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        JsonNode config = support.parseConfig(context.getStep().getConfigJson());
        int timeoutDays = support.number(config, "timeoutDays", DEFAULT_TIMEOUT_DAYS);
        if (timeoutDays <= 0) {
            timeoutDays = DEFAULT_TIMEOUT_DAYS;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(timeoutDays);

        // 查询当前会议下 PENDING_DECISION 态、且提交完成超过 timeoutDays 仍未裁决的待办
        // pendingDecision=true + decisionMadeAt IS NULL + createdAt < cutoff
        List<MeetingTodo> pending = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .eq(MeetingTodo::getStatus, TodoStatus.PENDING_DECISION.name())
                .eq(MeetingTodo::getPendingDecision, true)
                .isNull(MeetingTodo::getDecisionMadeAt)
                .lt(MeetingTodo::getCreatedAt, cutoff));

        int reminded = 0;
        for (MeetingTodo todo : pending) {
            try {
                feishuTaskService.notifyDecisionMaker(todo);
                reminded++;
            } catch (Exception e) {
                log.warn("remind decision maker failed: todoId={}, err={}", todo.getId(), e.getMessage());
            }
        }
        ObjectNode out = support.newObject();
        out.put("scanned", pending.size());
        out.put("reminded", reminded);
        out.put("timeoutDays", timeoutDays);
        return StepExecutionResult.ok("decision-timeout-scan", support.toJson(out));
    }
}
