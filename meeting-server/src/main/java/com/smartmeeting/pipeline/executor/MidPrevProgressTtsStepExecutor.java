package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
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
public class MidPrevProgressTtsStepExecutor implements StepExecutor {

    private final TodoMapper todoMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "mid-prev-progress-tts";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getPreviousMeetingId() == null || meeting.getPreviousMeetingId().isBlank()) {
            return StepExecutionResult.ok("no-previous-meeting", "{\"hasPrevious\":false}");
        }
        List<MeetingTodo> rows = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, meeting.getPreviousMeetingId()));
        long completed = rows.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
        long delayed = rows.stream().filter(t -> "DELAYED".equalsIgnoreCase(t.getStatus())).count();
        long inProgress = rows.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus())).count();
        String summary = "上次会议待办进度：共 " + rows.size() + " 项，已完成 " + completed
                + "，进行中 " + inProgress + "，延期 " + delayed + "。";
        if (meeting.getChatId() != null && !meeting.getChatId().isBlank()) {
            feishuService.sendMessage(meeting.getChatId(), summary);
        }
        return StepExecutionResult.ok("prev-progress-ready",
                "{\"hasPrevious\":true,\"summary\":\"" + summary.replace("\"", "\\\"") + "\"}");
    }
}

