package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.MeetingMapper;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PostAutoDelayedStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final MeetingMapper meetingMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "post-auto-delayed";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        List<MeetingTodo> overdue = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, context.getMeetingId())
                .in(MeetingTodo::getStatus, List.of("PENDING", "IN_PROGRESS"))
                .le(MeetingTodo::getDeadline, LocalDateTime.now())
                .last("LIMIT 200"));
        int affected = 0;
        for (MeetingTodo todo : overdue) {
            todo.setStatus("DELAYED");
            todoMapper.updateById(todo);
            affected++;
        }
        if (affected > 0) {
            Meeting meeting = meetingMapper.selectById(context.getMeetingId());
            if (meeting != null && meeting.getCreatorId() != null && !meeting.getCreatorId().isBlank()) {
                feishuService.sendMessageToUserId(meeting.getCreatorId(),
                        "待办延期提醒：会议「" + meeting.getTitle() + "」有 " + affected + " 项待办已自动标记为 DELAYED。");
            }
        }
        return StepExecutionResult.ok("post-auto-delayed-ok", "{\"affected\":" + affected + "}");
    }
}

