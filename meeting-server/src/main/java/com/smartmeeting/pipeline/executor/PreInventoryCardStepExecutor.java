package com.smartmeeting.pipeline.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmeeting.entity.Meeting;
import com.smartmeeting.entity.MeetingTodo;
import com.smartmeeting.entity.Participant;
import com.smartmeeting.pipeline.StepExecutionContext;
import com.smartmeeting.pipeline.StepExecutionResult;
import com.smartmeeting.pipeline.StepExecutor;
import com.smartmeeting.repository.TodoMapper;
import com.smartmeeting.repository.ParticipantMapper;
import com.smartmeeting.service.FeishuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PreInventoryCardStepExecutor implements StepExecutor {

    private final ParticipantMapper participantMapper;
    private final TodoMapper todoMapper;
    private final FeishuService feishuService;

    @Override
    public String stepType() {
        return "pre-inventory-card";
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) {
        Meeting meeting = context.getMeeting();
        if (meeting == null || meeting.getChatId() == null || meeting.getChatId().isBlank()) {
            return StepExecutionResult.failed("meeting/chatId missing");
        }
        List<Participant> participants = participantMapper.selectList(new LambdaQueryWrapper<Participant>()
                .eq(Participant::getMeetingId, meeting.getId()));
        long readyVoiceprint = participants.stream().filter(p -> Boolean.TRUE.equals(p.getVoiceprintReady())).count();
        long confirmed = participants.stream().filter(p -> "CONFIRMED".equalsIgnoreCase(p.getStatus())).count();
        long pending = participants.size() - confirmed;

        List<MeetingTodo> delayed = todoMapper.selectList(new LambdaQueryWrapper<MeetingTodo>()
                .eq(MeetingTodo::getMeetingId, meeting.getPreviousMeetingId())
                .eq(MeetingTodo::getStatus, "DELAYED"));

        String text = "会前盘点：\n"
                + "会议：" + meeting.getTitle() + "\n"
                + "参会确认：已确认 " + confirmed + " / 待确认 " + pending + "\n"
                + "声纹就绪：" + readyVoiceprint + " / " + participants.size() + "\n"
                + "上次延期待办：" + delayed.size() + " 项";
        boolean ok = feishuService.sendMessage(meeting.getChatId(), text);
        return StepExecutionResult.ok(ok ? "pre-inventory-ok" : "pre-inventory-failed",
                "{\"participants\":" + participants.size() + ",\"delayedTodos\":" + delayed.size() + "}");
    }
}

