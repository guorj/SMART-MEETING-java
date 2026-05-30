package com.smartmeeting.pipeline.executor;

import com.smartmeeting.entity.Meeting;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PreAgendaNotifyStepExecutor implements StepExecutor {

    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "pre-agenda-notify";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getChatId() == null || meeting.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        String text = "会前议程通知：\n"
                + "会议：" + meeting.getTitle() + "\n"
                + "集团：" + safe(meeting.getCompany()) + "\n"
                + "会议组：" + safe(meeting.getGroupName()) + "\n"
                + "请参会人提前阅读资料并准备发言。";
        boolean ok = feishuService.sendMessage(meeting.getChatId(), text);
        return StepExecutionResult.ok(ok ? "agenda-notify-ok" : "agenda-notify-failed",
                "{\"sent\":" + ok + "}");
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }
}

